package org.solovyev.android.messenger;

import android.app.Application;

import javax.annotation.Nonnull;

public class XmppTestModule extends DefaultTestModule {

	public XmppTestModule(@Nonnull Application application) {
		super(application);
	}

	@Nonnull
	@Override
	protected Class<? extends Configuration> getConfigurationClass() {
		return XmppTestConfiguration.class;
	}
}
