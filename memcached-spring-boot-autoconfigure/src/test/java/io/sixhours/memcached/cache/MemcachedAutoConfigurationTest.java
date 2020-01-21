/**
 * Copyright 2016-2020 Sixhours
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sixhours.memcached.cache;

import io.sixhours.memcached.cache.MemcachedCacheProperties.Protocol;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.sixhours.memcached.cache.MemcachedAssertions.assertMemcachedCacheManager;
import static io.sixhours.memcached.cache.MemcachedAssertions.assertMemcachedClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Memcached auto-configuration tests.
 *
 * @author Igor Bolic
 * @author Sasa Bolic
 */
public class MemcachedAutoConfigurationTest {

    private final AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();

    @After
    public void tearDown() {
        applicationContext.close();
    }

    @Test
    public void whenCachingNotEnabledThenMemcachedNotLoaded() {
        loadContext(EmptyConfiguration.class);

        assertThatThrownBy(() ->
                this.applicationContext.getBean(MemcachedCacheManager.class)
        )
                .isInstanceOf(NoSuchBeanDefinitionException.class)
                .hasMessage("No qualifying bean of type 'io.sixhours.memcached.cache.MemcachedCacheManager' available");
    }

    @Test
    public void whenCacheTypeIsNoneThenMemcachedNotLoaded() {
        loadContext(CacheConfiguration.class, "spring.cache.type=none");

        assertThatThrownBy(() ->
                this.applicationContext.getBean(MemcachedCacheManager.class)
        )
                .isInstanceOf(NoSuchBeanDefinitionException.class)
                .hasMessage("No qualifying bean of type 'io.sixhours.memcached.cache.MemcachedCacheManager' available");
    }

    @Test
    public void whenCacheTypeIsNoneThenNoOpCacheLoaded() {
        loadContext(CacheConfiguration.class, "spring.cache.type=none");

        CacheManager cacheManager = this.applicationContext.getBean(CacheManager.class);

        assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
    }

    @Test
    public void whenCacheTypeIsSimpleThenMemcachedNotLoaded() {
        loadContext(CacheConfiguration.class, "spring.cache.type=simple");

        assertThatThrownBy(() ->
                this.applicationContext.getBean(MemcachedCacheManager.class)
        )
                .isInstanceOf(NoSuchBeanDefinitionException.class)
                .hasMessage("No qualifying bean of type 'io.sixhours.memcached.cache.MemcachedCacheManager' available");
    }

    @Test
    public void whenCacheTypeIsSimpleThenSimpleCacheLoaded() {
        loadContext(CacheConfiguration.class, "spring.cache.type=simple");

        CacheManager cacheManager = this.applicationContext.getBean(CacheManager.class);

        assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
    }

    @Test
    public void whenCacheTypeIsInvalidThenContextNotLoaded() {
        assertThatThrownBy(() ->
                loadContext(CacheConfiguration.class, "spring.cache.type=invalid-type")
        )
                .isInstanceOf(BeanCreationException.class)
                .hasMessageContaining("Failed to bind properties under 'spring.cache.type' to org.springframework.boot.autoconfigure.cache.CacheType");
    }

    @Test
    public void whenUsingCustomCacheManagerThenMemcachedNotLoaded() {
        loadContext(CacheWithCustomCacheManagerConfiguration.class);

        assertThatThrownBy(() ->
                this.applicationContext.getBean(MemcachedCacheManager.class)
        )
                .isInstanceOf(NoSuchBeanDefinitionException.class)
                .hasMessage("No qualifying bean of type 'io.sixhours.memcached.cache.MemcachedCacheManager' available");
    }

    @Test
    public void whenUsingCustomCacheManagerThenMemcachedCustomCacheManagerLoaded() {
        loadContext(CacheWithCustomCacheManagerConfiguration.class);

        CacheManager cacheManager = this.applicationContext.getBean(CacheManager.class);

        assertThat(cacheManager).isInstanceOf(ConcurrentMapCacheManager.class);
    }

    @Test
    public void whenNoCustomCacheManagerThenMemcachedCacheManagerLoaded() {
        loadContext(CacheConfiguration.class);

        CacheManager cacheManager = this.applicationContext.getBean(CacheManager.class);

        assertThat(cacheManager).isInstanceOf(MemcachedCacheManager.class);
    }

    @Ignore
    @Test
    public void whenNoCustomCacheManagerThenMemcachedWithDefaultConfigurationLoaded() {
        loadContext(CacheConfiguration.class);

        MemcachedCacheManager memcachedCacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        IMemcachedClient memcachedClient = (IMemcachedClient) ReflectionTestUtils.getField(memcachedCacheManager, "memcachedClient");

        assertMemcachedClient(memcachedClient, Default.PROTOCOL, Default.OPERATION_TIMEOUT, Default.SERVERS.toArray(new InetSocketAddress[0]));
        assertMemcachedCacheManager(memcachedCacheManager, Default.EXPIRATION, null, Default.PREFIX, Default.NAMESPACE);
    }

    @Test
    public void whenRefreshAutoConfigurationThenRefreshConfigurationLoaded() {
        loadContext(CacheWithRefreshAutoConfiguration.class);

        assertThat(this.applicationContext
                .getBeanDefinition("scopedTarget.cacheManager").getScope()).isEqualTo("refresh");
    }

    @Ignore
    @Test
    public void whenRefreshAutoConfigurationThenDefaultConfigurationLoaded() {
        loadContext(CacheWithRefreshAutoConfiguration.class);

        MemcachedCacheManager cacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        IMemcachedClient memcachedClient = (IMemcachedClient) ReflectionTestUtils.getField(cacheManager, "memcachedClient");

        assertMemcachedClient(memcachedClient, Default.PROTOCOL, Default.OPERATION_TIMEOUT, Default.SERVERS.toArray(new InetSocketAddress[0]));
        assertMemcachedCacheManager(cacheManager, Default.EXPIRATION, null, Default.PREFIX, Default.NAMESPACE);
    }

    @Test
    public void whenMemcachedCacheManagerBeanAlreadyInContextThenMemcachedWithNonCustomConfigurationLoaded() {
        loadContext(CacheWithMemcachedCacheManagerConfiguration.class, "memcached.cache.expiration=3600",
                "memcached.cache.prefix=custom:prefix",
                "memcached.cache.namespace=custom_namespace");

        MemcachedCacheManager memcachedCacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        assertThat(memcachedCacheManager)
                .as("Auto-configured disposable instance should not be loaded in context")
                .isNotInstanceOf(DisposableMemcachedCacheManager.class);
        assertMemcachedCacheManager(memcachedCacheManager, Default.EXPIRATION, null, Default.PREFIX, Default.NAMESPACE);
    }

    @Test
    public void whenAppEngineProviderThenAppEngineMemcachedLoaded() {
        loadContext(CacheConfiguration.class, "memcached.cache.provider=appengine");

        CacheManager cacheManager = this.applicationContext.getBean(CacheManager.class);

        assertThat(cacheManager).isInstanceOf(DisposableMemcachedCacheManager.class);
        assertThat(((DisposableMemcachedCacheManager) cacheManager).memcachedClient).isInstanceOf(AppEngineMemcachedClient.class);
    }

    @Ignore
    @Test
    public void whenAwsProviderAndMultipleServerListThenMemcachedNotLoaded() {
        assertThatThrownBy(() ->
                loadContext(CacheConfiguration.class, "memcached.cache.servers=192.168.99.100:11212, 192.168.99.101:11211",
                        "memcached.cache.provider=aws")
        )
                .isInstanceOf(BeanCreationException.class)
                .hasCauseInstanceOf(BeanInstantiationException.class)
                .hasStackTraceContaining("Only one configuration endpoint is valid with dynamic client mode.");
    }

    @Ignore
    @Test
    public void whenStaticProviderAndMultipleServerListThenMemcachedLoaded() {
        loadContext(CacheConfiguration.class, "memcached.cache.servers=192.168.99.100:11212,192.168.99.101:11211",
                "memcached.cache.provider=static");

        MemcachedCacheManager memcachedCacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        IMemcachedClient memcachedClient = (IMemcachedClient) ReflectionTestUtils.getField(memcachedCacheManager, "memcachedClient");

        assertMemcachedClient(memcachedClient, Default.PROTOCOL, Default.OPERATION_TIMEOUT, new InetSocketAddress("192.168.99.100", 11212), new InetSocketAddress("192.168.99.101", 11211));
        assertMemcachedCacheManager(memcachedCacheManager, Default.EXPIRATION, null, Default.PREFIX, Default.NAMESPACE);
    }

    @Ignore
    @Test
    public void whenTextProtocolAndMultipleServerListThenMemcachedLoaded() {
        loadContext(CacheConfiguration.class, "memcached.cache.servers=192.168.99.100:11212,192.168.99.101:11211",
                "memcached.cache.protocol=text");

        MemcachedCacheManager memcachedCacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        IMemcachedClient memcachedClient = (IMemcachedClient) ReflectionTestUtils.getField(memcachedCacheManager, "memcachedClient");

        assertMemcachedClient(memcachedClient, Protocol.TEXT, Default.OPERATION_TIMEOUT, new InetSocketAddress("192.168.99.100", 11212), new InetSocketAddress("192.168.99.101", 11211));
        assertMemcachedCacheManager(memcachedCacheManager, Default.EXPIRATION, null, Default.PREFIX, Default.NAMESPACE);
    }

    @Ignore
    @Test
    public void whenBinaryProtocolAndMultipleServerListThenMemcachedLoaded() {
        loadContext(CacheConfiguration.class, "memcached.cache.servers=192.168.99.100:11212,192.168.99.101:11211",
                "memcached.cache.protocol=binary");

        MemcachedCacheManager memcachedCacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        IMemcachedClient memcachedClient = (IMemcachedClient) ReflectionTestUtils.getField(memcachedCacheManager, "memcachedClient");

        assertMemcachedClient(memcachedClient, Protocol.BINARY, Default.OPERATION_TIMEOUT, new InetSocketAddress("192.168.99.100", 11212), new InetSocketAddress("192.168.99.101", 11211));
        assertMemcachedCacheManager(memcachedCacheManager, Default.EXPIRATION, null, Default.PREFIX, Default.NAMESPACE);
    }

    @Test
    public void whenStaticModeAndEmptyServerListThenMemcachedNotLoaded() {
        assertThatThrownBy(() ->
                loadContext(CacheConfiguration.class, "memcached.cache.servers=",
                        "memcached.cache.type=static")
        )
                .isInstanceOf(UnsatisfiedDependencyException.class)
                .hasRootCause(new IllegalArgumentException("Server list is empty"));
    }

    @Test
    public void whenOperationTimeoutInvalidThenMemcachedNotLoaded() {
        assertThatThrownBy(() -> loadContext(CacheConfiguration.class,
                "memcached.cache.operation-timeout=0"))
                .isInstanceOf(UnsatisfiedDependencyException.class)
                .hasRootCause(new IllegalArgumentException("Operation timeout must be greater then zero"));
    }

    @Ignore
    @Test
    public void whenCustomConfigurationThenMemcachedLoaded() {
        loadContext(CacheConfiguration.class, "memcached.cache.servers=192.168.99.100:11212",
                "memcached.cache.type=static",
                "memcached.cache.expiration=3600",
                "memcached.cache.expiration-per-cache.myKey1=4000",
                "memcached.cache.prefix=custom:prefix",
                "memcached.cache.operation-timeout=3000",
                "memcached.cache.namespace=custom_namespace");

        MemcachedCacheManager memcachedCacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        IMemcachedClient memcachedClient = (IMemcachedClient) ReflectionTestUtils.getField(memcachedCacheManager, "memcachedClient");

        assertMemcachedClient(memcachedClient, Default.PROTOCOL, 3000, new InetSocketAddress("192.168.99.100", 11212));
        assertMemcachedCacheManager(memcachedCacheManager, 3600, Collections.singletonMap("myKey1", 4000), "custom:prefix", Default.NAMESPACE);
    }

    @Ignore
    @Test
    public void whenPartialConfigurationValuesThenMemcachedLoaded() {
        loadContext(CacheConfiguration.class, "memcached.cache.servers=192.168.99.100:12345",
                "memcached.cache.prefix=custom:prefix");

        MemcachedCacheManager memcachedCacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        IMemcachedClient memcachedClient = (IMemcachedClient) ReflectionTestUtils.getField(memcachedCacheManager, "memcachedClient");

        assertMemcachedClient(memcachedClient, Default.PROTOCOL, Default.OPERATION_TIMEOUT, new InetSocketAddress("192.168.99.100", 12345));
        assertMemcachedCacheManager(memcachedCacheManager, Default.EXPIRATION, null, "custom:prefix", Default.NAMESPACE);
    }

    @Test
    public void whenExpirationsValuesGiven() {
        loadContext(CacheConfiguration.class,
                "memcached.cache.expiration=8000","memcached.cache.expiration-per-cache.testKey1=4000","memcached.cache.expiration-per-cache.testKey2=5000","memcached.cache.expiration-per-cache.testKey3=6000","memcached.cache.expiration-per-cache.testKey4=7000");

        MemcachedCacheManager memcachedCacheManager = this.applicationContext.getBean(MemcachedCacheManager.class);

        IMemcachedClient memcachedClient = (IMemcachedClient) ReflectionTestUtils.getField(memcachedCacheManager, "memcachedClient");

        assertMemcachedClient(memcachedClient, Default.PROTOCOL, Default.OPERATION_TIMEOUT, new InetSocketAddress("localhost", 11211));

        final Map<String, Integer> expirationPerCache = Stream.of(new Object[][]{
                {"testKey1", 4000},
                {"testKey2", 5000},
                {"testKey3", 6000},
                {"testKey4", 7000},
        }).collect(Collectors.toMap(e -> (String) e[0], e -> (Integer) e[1]));

        assertMemcachedCacheManager(memcachedCacheManager, 8000, expirationPerCache, Default.PREFIX, Default.NAMESPACE);
    }

    private void loadContext(Class<?> configuration, String... environment) {
        TestPropertyValues.of(environment).applyTo(applicationContext);

        applicationContext.register(configuration);
        applicationContext.register(MemcachedCacheAutoConfiguration.class);
        applicationContext.register(CacheAutoConfiguration.class);
        applicationContext.refresh();
    }

    @Configuration
    static class EmptyConfiguration {
    }

    @Configuration
    @EnableCaching
    static class CacheConfiguration {
    }

    @Configuration
    static class CacheWithCustomCacheManagerConfiguration extends CacheConfiguration {

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @Configuration
    static class CacheWithMemcachedCacheManagerConfiguration extends CacheConfiguration {

        @Bean
        public MemcachedCacheManager cacheManager() {
            IMemcachedClient memcachedClient = mock(IMemcachedClient.class);

            return new MemcachedCacheManager(memcachedClient);
        }
    }

    @Configuration
    @Import(RefreshAutoConfiguration.class)
    static class CacheWithRefreshAutoConfiguration extends CacheConfiguration {
    }

}
