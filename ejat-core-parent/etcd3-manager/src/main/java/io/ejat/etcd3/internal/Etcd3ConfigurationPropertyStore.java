package io.ejat.etcd3.internal;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;

import org.osgi.service.component.annotations.Component;

import io.ejat.framework.spi.ConfigurationPropertyStoreException;
import io.ejat.framework.spi.IConfigurationPropertyStore;
import io.ejat.framework.spi.IFrameworkInitialisation;

//@Component(service= {IConfigurationPropertyStore.class})
public class Etcd3ConfigurationPropertyStore implements IConfigurationPropertyStore {

	//@Override
	public void initialise(@NotNull IFrameworkInitialisation frameworkInitialisation)
			throws ConfigurationPropertyStoreException {
		System.out.println("here");
	}

	@Override
	public @Null String getProperty(@NotNull String key) throws ConfigurationPropertyStoreException {
		return null;
	}
}