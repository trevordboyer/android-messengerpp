package org.solovyev.android.messenger.realms;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * User: serso
 * Date: 3/9/13
 * Time: 2:45 PM
 */
public enum RealmEventType {

    /**
     * Fires when realm is created
     */
    created,

    /**
     * Fires when realm is changed
     */
    changed,

    /**
     * Fires when realm state is changed
     */
    state_changed,

    /**
     * Fires when realm connection should be stopped for realm
     */
    stop,

    /**
     * Fires when realm connection should be started for realm
     */
    start;

    @Nonnull
    RealmEvent newEvent(@Nonnull Realm realm, @Nullable Object data) {
        assert data == null;
        return new RealmEvent(realm, this, null);
    }
}
