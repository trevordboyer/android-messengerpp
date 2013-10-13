package org.solovyev.android.messenger.messages;

import android.app.Application;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.google.common.base.Predicate;
import com.google.inject.Inject;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.solovyev.android.db.*;
import org.solovyev.android.messenger.MergeDaoResult;
import org.solovyev.android.messenger.MergeDaoResultImpl;
import org.solovyev.android.messenger.chats.Chat;
import org.solovyev.android.messenger.chats.ChatService;
import org.solovyev.android.messenger.db.StringIdMapper;
import org.solovyev.android.messenger.entities.Entities;
import org.solovyev.android.messenger.entities.Entity;
import org.solovyev.android.messenger.users.UserService;
import org.solovyev.common.collections.Collections;
import org.solovyev.common.text.Strings;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import static com.google.common.base.Predicates.equalTo;
import static com.google.common.collect.Iterables.find;
import static org.solovyev.android.db.AndroidDbUtils.*;

/**
 * User: serso
 * Date: 6/11/12
 * Time: 7:41 PM
 */
@Singleton
public class SqliteChatMessageDao extends AbstractSQLiteHelper implements ChatMessageDao {

    /*
	**********************************************************************
    *
    *                           AUTO INJECTED FIELDS
    *
    **********************************************************************
    */

	@Inject
	@Nonnull
	private ChatService chatService;

	@Inject
	@Nonnull
	private UserService userService;

	@Inject
	public SqliteChatMessageDao(@Nonnull Application context, @Nonnull SQLiteOpenHelper sqliteOpenHelper) {
		super(context, sqliteOpenHelper);
	}

	@Nonnull
	@Override
	public List<String> readMessageIds(@Nonnull String chatId) {
		return doDbQuery(getSqliteOpenHelper(), new LoadChatMessageIdsByChatId(getContext(), chatId, getSqliteOpenHelper()));
	}

	@Nonnull
	@Override
	public List<ChatMessage> readMessages(@Nonnull String chatId) {
		return doDbQuery(getSqliteOpenHelper(), new LoadChatMessages(getContext(), chatId, this.userService, getSqliteOpenHelper()));
	}

	@Nonnull
	@Override
	public String getOldestMessageForChat(@Nonnull String chatId) {
		return doDbQuery(getSqliteOpenHelper(), new OldestChatMessageLoader(getContext(), getSqliteOpenHelper(), chatId));
	}

	@Nullable
	@Override
	public ChatMessage readLastMessage(@Nonnull String chatId) {
		final String lastChatMessageId = doDbQuery(getSqliteOpenHelper(), new LastChatMessageLoader(getContext(), getSqliteOpenHelper(), chatId));
		if (!Strings.isEmpty(lastChatMessageId)) {
			final List<ChatMessage> messages = doDbQuery(getSqliteOpenHelper(), new LoadChatMessage(getContext(), lastChatMessageId, this.userService, getSqliteOpenHelper()));
			return Collections.getFirstListElement(messages);
		} else {
			return null;
		}
	}

	@Override
	public int getUnreadMessagesCount() {
		return doDbQuery(getSqliteOpenHelper(), new UnreadMessagesCountLoader(getContext(), getSqliteOpenHelper()));
	}

	@Override
	public boolean changeReadStatus(@Nonnull String messageId, boolean read) {
		final Long rows = doDbExec(getSqliteOpenHelper(), new ReadMessageStatusUpdater(messageId, read));
		return rows != 0;
	}

	@Override
	public void deleteAll() {
		doDbExec(getSqliteOpenHelper(), DeleteAllRowsDbExec.newInstance("messages"));
	}

	@Nonnull
	@Override
	public MergeDaoResult<ChatMessage, String> mergeMessages(@Nonnull String chatId, @Nonnull Collection<? extends ChatMessage> messages, boolean allowDelete) {
		final MergeDaoResultImpl<ChatMessage, String> result = new MergeDaoResultImpl<ChatMessage, String>();

		final Chat chat = getChatService().getChatById(Entities.newEntityFromEntityId(chatId));

		if (chat != null) {
			final List<String> messageIdsFromDb = readMessageIds(chatId);
			for (final String chatMessageIdFromDb : messageIdsFromDb) {
				try {
					// message exists both in db and on remote server => just update message properties
					result.addUpdatedObject(find(messages, new ChatMessageByIdFinder(chatMessageIdFromDb)));
				} catch (NoSuchElementException e) {
					// message was removed on remote server => need to remove from local db
					result.addRemovedObjectId(chatMessageIdFromDb);
				}
			}

			for (ChatMessage message : messages) {
				try {
					// message exists both in db and on remote server => case already covered above
					find(messageIdsFromDb, equalTo(message.getEntity().getEntityId()));
				} catch (NoSuchElementException e) {
					// message was added on remote server => need to add to local db
					if (!messageIdsFromDb.contains(message.getEntity().getEntityId())) {
						// no message information in local db is available - full message insertion
						result.addAddedObject(message);
					}
				}
			}

			final List<DbExec> execs = new ArrayList<DbExec>();

			if (allowDelete) {
				if (!result.getRemovedObjectIds().isEmpty()) {
					execs.addAll(RemoveMessages.newInstances(result.getRemovedObjectIds()));
				}
			}

			for (ChatMessage updatedMessage : result.getUpdatedObjects()) {
				execs.add(new UpdateMessage(updatedMessage, chat));
			}

			for (ChatMessage addedMessage : result.getAddedObjects()) {
				execs.add(new InsertMessage(chat, addedMessage));
			}

			doDbExecs(getSqliteOpenHelper(), execs);
		}

		return result;
	}

	@Nonnull
	private ChatService getChatService() {
		return this.chatService;
	}

	public void setChatService(@Nonnull ChatService chatService) {
		this.chatService = chatService;
	}

	public void setUserService(@Nonnull UserService userService) {
		this.userService = userService;
	}

	/*
    **********************************************************************
    *
    *                           STATIC
    *
    **********************************************************************
    */

	private static class LoadChatMessageIdsByChatId extends AbstractDbQuery<List<String>> {

		@Nonnull
		private final String chatId;

		private LoadChatMessageIdsByChatId(@Nonnull Context context, @Nonnull String chatId, @Nonnull SQLiteOpenHelper sqliteOpenHelper) {
			super(context, sqliteOpenHelper);
			this.chatId = chatId;
		}

		@Nonnull
		@Override
		public Cursor createCursor(@Nonnull SQLiteDatabase db) {
			return db.query("messages", null, "chat_id = ? ", new String[]{String.valueOf(chatId)}, null, null, null);
		}

		@Nonnull
		@Override
		public List<String> retrieveData(@Nonnull Cursor cursor) {
			return new ListMapper<String>(StringIdMapper.getInstance()).convert(cursor);
		}
	}

	private static class ChatMessageByIdFinder implements Predicate<ChatMessage> {

		@Nonnull
		private final String messageId;

		public ChatMessageByIdFinder(@Nonnull String messageId) {
			this.messageId = messageId;
		}

		@Override
		public boolean apply(@javax.annotation.Nullable ChatMessage message) {
			return message != null && message.getEntity().getEntityId().equals(messageId);
		}
	}

	public static final class InsertMessage extends AbstractObjectDbExec<ChatMessage> {

		@Nonnull
		private final Chat chat;

		public InsertMessage(@Nonnull Chat chat, @Nullable ChatMessage chatMessage) {
			super(chatMessage);
			this.chat = chat;
		}

		@Override
		public long exec(@Nonnull SQLiteDatabase db) {
			final ChatMessage chatMessage = getNotNullObject();

			final ContentValues values = toContentValues(chat, chatMessage);

			return db.insert("messages", null, values);
		}
	}

	private static final class UpdateMessage extends AbstractObjectDbExec<ChatMessage> {

		@Nonnull
		private final Chat chat;

		private UpdateMessage(@Nonnull ChatMessage chatMessage, @Nonnull Chat chat) {
			super(chatMessage);
			this.chat = chat;
		}

		@Override
		public long exec(@Nonnull SQLiteDatabase db) {
			final ChatMessage chatMessage = getNotNullObject();

			final ContentValues values = toContentValues(chat, chatMessage);

			return db.update("messages", values, "id = ? and chat_id = ?", new String[]{String.valueOf(chatMessage.getEntity().getEntityId()), String.valueOf(chat.getEntity().getEntityId())});
		}
	}

	private static final class RemoveMessages implements DbExec {

		@Nonnull
		private List<String> messagesIds;

		private RemoveMessages(@Nonnull List<String> messagesIds) {
			this.messagesIds = messagesIds;
		}

		@Nonnull
		private static List<RemoveMessages> newInstances(@Nonnull List<String> messagesIds) {
			final List<RemoveMessages> result = new ArrayList<RemoveMessages>();

			for (List<String> messagesIdsChunk : Collections.split(messagesIds, MAX_IN_COUNT)) {
				result.add(new RemoveMessages(messagesIdsChunk));
			}

			return result;
		}

		@Override
		public long exec(@Nonnull SQLiteDatabase db) {
			return db.delete("messages", "chat_id in " + inClause(messagesIds), inClauseValues(messagesIds));
		}
	}


	private static final class LoadChatMessages extends AbstractDbQuery<List<ChatMessage>> {

		@Nonnull
		private final String chatId;

		@Nonnull
		private final UserService userService;

		private LoadChatMessages(@Nonnull Context context,
								 @Nonnull String chatId,
								 @Nonnull UserService userService,
								 @Nonnull SQLiteOpenHelper sqliteOpenHelper) {
			super(context, sqliteOpenHelper);
			this.chatId = chatId;
			this.userService = userService;
		}

		@Nonnull
		@Override
		public Cursor createCursor(@Nonnull SQLiteDatabase db) {
			return db.query("messages", null, "chat_id = ? ", new String[]{chatId}, null, null, null);
		}

		@Nonnull
		@Override
		public List<ChatMessage> retrieveData(@Nonnull Cursor cursor) {
			return new ListMapper<ChatMessage>(new ChatMessageMapper(this.userService)).convert(cursor);
		}
	}

	private static final class LoadChatMessage extends AbstractDbQuery<List<ChatMessage>> {

		@Nonnull
		private final String messageId;

		@Nonnull
		private final UserService userService;

		private LoadChatMessage(@Nonnull Context context,
								@Nonnull String messageId,
								@Nonnull UserService userService,
								@Nonnull SQLiteOpenHelper sqliteOpenHelper) {
			super(context, sqliteOpenHelper);
			this.messageId = messageId;
			this.userService = userService;
		}

		@Nonnull
		@Override
		public Cursor createCursor(@Nonnull SQLiteDatabase db) {
			return db.query("messages", null, "id = ? ", new String[]{String.valueOf(messageId)}, null, null, null);
		}

		@Nonnull
		@Override
		public List<ChatMessage> retrieveData(@Nonnull Cursor cursor) {
			return new ListMapper<ChatMessage>(new ChatMessageMapper(this.userService)).convert(cursor);
		}
	}

	private static class OldestChatMessageLoader extends AbstractDbQuery<String> {

		@Nonnull
		private String chatId;

		protected OldestChatMessageLoader(@Nonnull Context context, @Nonnull SQLiteOpenHelper sqliteOpenHelper, @Nonnull String chatId) {
			super(context, sqliteOpenHelper);
			this.chatId = chatId;
		}

		@Nonnull
		@Override
		public Cursor createCursor(@Nonnull SQLiteDatabase db) {
			return db.rawQuery("select id from messages where chat_id = ? order by send_time asc", new String[]{chatId});
		}

		@Nonnull
		@Override
		public String retrieveData(@Nonnull Cursor cursor) {
			if (cursor.moveToFirst()) {
				return cursor.getString(0);
			} else {
				return "";
			}
		}
	}

	private static class LastChatMessageLoader extends AbstractDbQuery<String> {

		@Nonnull
		private String chatId;

		protected LastChatMessageLoader(@Nonnull Context context, @Nonnull SQLiteOpenHelper sqliteOpenHelper, @Nonnull String chatId) {
			super(context, sqliteOpenHelper);
			this.chatId = chatId;
		}

		@Nonnull
		@Override
		public Cursor createCursor(@Nonnull SQLiteDatabase db) {
			return db.rawQuery("select id from messages where chat_id = ? order by send_time desc", new String[]{chatId});
		}

		@Nonnull
		@Override
		public String retrieveData(@Nonnull Cursor cursor) {
			if (cursor.moveToFirst()) {
				return cursor.getString(0);
			} else {
				return "";
			}
		}
	}

	@Nonnull
	private static ContentValues toContentValues(@Nonnull Chat chat, @Nonnull ChatMessage message) {
		final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.basicDateTime();

		final ContentValues values = new ContentValues();

		final Entity entity = message.getEntity();
		values.put("id", entity.getEntityId());
		values.put("account_id", entity.getAccountId());
		values.put("realm_message_id", entity.getAccountEntityId());

		values.put("chat_id", chat.getEntity().getEntityId());
		values.put("author_id", message.getAuthor().getEntityId());
		final Entity recipient = message.getRecipient();
		values.put("recipient_id", recipient == null ? null : recipient.getEntityId());
		values.put("send_date", dateTimeFormatter.print(message.getSendDate()));
		values.put("send_time", message.getSendDate().getMillis());
		values.put("title", message.getTitle());
		values.put("body", message.getBody());
		values.put("read", message.isRead() ? 1 : 0);
		values.put("state", message.getState().name());
		return values;
	}

	private static class UnreadMessagesCountLoader extends AbstractDbQuery<Integer> {

		private UnreadMessagesCountLoader(@Nonnull Context context, @Nonnull SQLiteOpenHelper sqliteOpenHelper) {
			super(context, sqliteOpenHelper);
		}

		@Nonnull
		@Override
		public Cursor createCursor(@Nonnull SQLiteDatabase db) {
			return db.rawQuery("select count(*) from messages where read = 0", null);
		}

		@Nonnull
		@Override
		public Integer retrieveData(@Nonnull Cursor cursor) {
			if (cursor.moveToFirst()) {
				return cursor.getInt(0);
			} else {
				return 0;
			}
		}
	}

	private static class ReadMessageStatusUpdater implements DbExec {

		@Nonnull
		private final String messageId;

		private final boolean read;

		private ReadMessageStatusUpdater(@Nonnull String messageId, boolean read) {
			this.messageId = messageId;
			this.read = read;
		}

		@Override
		public long exec(@Nonnull SQLiteDatabase db) {
			final ContentValues values = new ContentValues();
			final int newReadValue = read ? 1 : 0;
			values.put("read", newReadValue);
			return db.update("messages", values, "id = ? and read <> ?", new String[]{messageId, String.valueOf(newReadValue)});
		}
	}
}
