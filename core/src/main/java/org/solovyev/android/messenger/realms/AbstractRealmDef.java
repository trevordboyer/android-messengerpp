package org.solovyev.android.messenger.realms;

import android.app.Activity;
import org.jetbrains.annotations.NotNull;

/**
 * User: serso
 * Date: 7/22/12
 * Time: 1:05 AM
 */
public abstract class AbstractRealmDef implements RealmDef {

    @NotNull
    private final String id;

    private final int nameResId;

    private final int iconResId;

    @NotNull
    private final Class<? extends Activity> configurationActivityClass;

    @NotNull
    private final Class<? extends RealmConfiguration> configurationClass;

    protected AbstractRealmDef(@NotNull String id,
                               int nameResId,
                               int iconResId,
                               @NotNull Class<? extends Activity> configurationActivityClass,
                               @NotNull Class<? extends RealmConfiguration> configurationClass) {
        this.id = id;
        this.nameResId = nameResId;
        this.iconResId = iconResId;
        this.configurationActivityClass = configurationActivityClass;
        this.configurationClass = configurationClass;
    }

    @NotNull
    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public int getNameResId() {
        return this.nameResId;
    }

    @Override
    public int getIconResId() {
        return this.iconResId;
    }

    @Override
    @NotNull
    public Class<? extends RealmConfiguration> getConfigurationClass() {
        return configurationClass;
    }

    @NotNull
    @Override
    public Class<? extends Activity> getConfigurationActivityClass() {
        return this.configurationActivityClass;
    }
}
