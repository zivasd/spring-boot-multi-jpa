package io.github.zivasd.spring.boot.jpa.cfg;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryBuilderCustomizer;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ConfigurationClassPostProcessor;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactoryBean;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationDelegate;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.repository.config.RepositoryConfigurationUtils;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.orm.jpa.vendor.AbstractJpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfiguration(value = "io.github.zivasd.spring.boot.jpa.cfg.JpaBeansBuilder", afterName = "io.github.zivasd.spring.boot.jdbc.cfg.DataSourceBeansBuilder")
@AutoConfigureBefore(value = HibernateJpaAutoConfiguration.class)
@ComponentScan({ "io.github.zivasd.spring.boot.jpa.cfg" })
@ConditionalOnBean(DataSource.class)
public class JpaBeansBuilder
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware, ResourceLoaderAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(JpaBeansBuilder.class);

    private static final String ENTITYMANAGER_FACTORY_POSTFIX = "EntityManagerFactory";
    private static final String TRANSACTION_MANAGER_POSTFIX = "TransactionManager";
    private static final String DATASOURCE_POSTFIX = "DataSource";

    private Map<String, JpaPropertiesExt> jpas;

    private Environment environment;
    private ResourceLoader resourceLoader;
    private ApplicationContext applicationContext;

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // not needed
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        registry.removeBeanDefinition(WillRemovedTemporarilyBeans.REMOVED_ENTITYMANAGER_FACTORYBUILDER);
        registry.removeBeanDefinition(WillRemovedTemporarilyBeans.REMOVE_ENTITYMANAGER_FACTORY);
        boolean doRegisterTM = registry
                .containsBeanDefinition(WillRemovedTemporarilyBeans.REMOVED_JPA_TRANSACTION_MANAGER1);
        if (doRegisterTM) {
            registry.removeBeanDefinition(WillRemovedTemporarilyBeans.REMOVED_JPA_TRANSACTION_MANAGER1);
            registry.removeBeanDefinition(WillRemovedTemporarilyBeans.REMOVED_JPA_TRANSACTION_MANAGER2);
        }

        registry.removeBeanDefinition(WillRemovedTemporarilyBeans.class.getName());

        Binder binder = Binder.get(environment);
        jpas = binder.bind("spring.jpas", Bindable.mapOf(String.class, JpaPropertiesExt.class)).get();

        if (jpas == null || jpas.size() == 0)
            return;

        boolean primary = true;
        for (Map.Entry<String, JpaPropertiesExt> entry : jpas.entrySet()) {
            registerEntityManagerFactoryBuild(registry, entry.getKey(), primary);
            registerEntityManagerFactory(registry, entry.getKey(), primary);
            if (doRegisterTM) {
                registerTransactionManager(registry, entry.getKey(), primary);
                registerTransactionTemplate(registry, entry.getKey(), primary);
            }
            primary = false;
        }

        for (Map.Entry<String, JpaPropertiesExt> entry : jpas.entrySet()) {
            String unit = entry.getKey();
            if (entry.getValue().getRepositories().getBasePackages().length == 0)
                continue;
            AnnotationMetadata metadata = createAnnotationMetadata(unit + ENTITYMANAGER_FACTORY_POSTFIX,
                    unit + TRANSACTION_MANAGER_POSTFIX, entry.getValue().getRepositories().getBasePackages());

            AnnotationRepositoryConfigurationSource configurationSource = new AnnotationRepositoryConfigurationSource(
                    metadata, EnableJpaRepositories.class,
                    JpaBeansBuilder.this.resourceLoader, JpaBeansBuilder.this.environment,
                    registry,
                    ConfigurationClassPostProcessor.IMPORT_BEAN_NAME_GENERATOR);

            RepositoryConfigurationExtension extension = new JpaRepositoryConfigExtension();
            RepositoryConfigurationUtils.exposeRegistration(extension, registry, configurationSource);
            RepositoryConfigurationDelegate delegate = new RepositoryConfigurationDelegate(configurationSource,
                    resourceLoader,
                    environment);
            delegate.registerRepositoriesIn(registry, extension);
        }
    }

    private void registerEntityManagerFactoryBuild(@NonNull BeanDefinitionRegistry registry, String unitName,
            boolean primary) {
        registerBean(registry, "entityManagerFactoryBuilder", unitName, "EntityManagerFactoryBuilder", primary);
    }

    private void registerEntityManagerFactory(@NonNull BeanDefinitionRegistry registry, String unitName,
            boolean primary) {
        registerBean(registry, "entityManagerFactory", unitName, ENTITYMANAGER_FACTORY_POSTFIX, primary);
    }

    private void registerTransactionManager(@NonNull BeanDefinitionRegistry registry, String unitName,
            boolean primary) {
        registerBean(registry, "transactionManager", unitName, TRANSACTION_MANAGER_POSTFIX, primary);
    }

    private void registerTransactionTemplate(@NonNull BeanDefinitionRegistry registry, @NonNull String unitName,
            boolean primary) {
        registerBean(registry, "transactionTemplate", unitName, "TransactionTemplate", primary);
    }

    public EntityManagerFactoryBuilder entityManagerFactoryBuilder(
            ObjectProvider<PersistenceUnitManager> persistenceUnitManager,
            ObjectProvider<EntityManagerFactoryBuilderCustomizer> customizers, String unit) {

        HibernateJpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
        JpaPropertiesExt jpaProperties = this.jpas.get(unit);
        AbstractJpaVendorAdapter adapter = jpaVendorAdapter;
        adapter.setDatabase(jpaProperties.getDatabase());
        adapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
        adapter.setGenerateDdl(jpaProperties.isGenerateDdl());
        adapter.setShowSql(jpaProperties.isShowSql());
        EntityManagerFactoryBuilder builder = new EntityManagerFactoryBuilder(jpaVendorAdapter,
                jpaProperties.getProperties(), persistenceUnitManager.getIfAvailable());
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }

    public LocalContainerEntityManagerFactoryBean entityManagerFactory(@NonNull String unit) {
        JpaPropertiesExt jpa = jpas.get(unit);
        EntityManagerFactoryBuilder builder = this.applicationContext.getBean(unit + "EntityManagerFactoryBuilder",
                EntityManagerFactoryBuilder.class);

        DataSource dataSourceObject = null;
        try {
            this.applicationContext.getBean(jpa.getDataSource(), DataSource.class);
        } catch (NoSuchBeanDefinitionException | BeanNotOfRequiredTypeException e) {
            LOGGER.info("Initialized JPA fault with DataSource: {} fault, Try  DataSource bean named {}.",
                    jpa.getDataSource(), jpa.getDataSource() + DATASOURCE_POSTFIX);
        }
        if (dataSourceObject == null) {
            try {
                dataSourceObject = this.applicationContext.getBean(jpa.getDataSource() + DATASOURCE_POSTFIX,
                        DataSource.class);
            } catch (NoSuchBeanDefinitionException | BeanNotOfRequiredTypeException e) {
                LOGGER.error(
                        "Initialized JPA fault with DataSource: {} fault!! Please check your DataSource configuration.",
                        jpa.getDataSource() + DATASOURCE_POSTFIX);
                throw e;
            }
        }

        Map<String, Object> properties = null;
        if (jpa.getHibernate() == null) {
            properties = new HashMap<>();
        } else {
            properties = jpa.getHibernate().determineHibernateProperties(jpa.getProperties(),
                    new HibernateSettings());
        }

        String packages = null;
        if (jpa.getBasePackages() != null) {
            jpa.getBasePackages();
            packages = String.join(",", jpa.getBasePackages());
        }

        return builder
                .dataSource(dataSourceObject)
                .persistenceUnit(unit + "PersistenceUnit")
                .properties(properties)
                .packages(packages)
                .jta(false)
                .build();
    }

    public PlatformTransactionManager transactionManager(String unit) {
        EntityManagerFactory entityManagerFactoryBean = this.applicationContext
                .getBean(unit + ENTITYMANAGER_FACTORY_POSTFIX, EntityManagerFactory.class);
        return new JpaTransactionManager(entityManagerFactoryBean);
    }

    public TransactionTemplate transactionTemplate(String unit) {
        PlatformTransactionManager transactionManager = this.applicationContext.getBean(
                unit + TRANSACTION_MANAGER_POSTFIX,
                PlatformTransactionManager.class);
        return createTransactionTemplate(transactionManager);
    }

    private TransactionTemplate createTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    private void registerBean(@NonNull BeanDefinitionRegistry registry, String factoryMethodName, String unit,
            String postfix, boolean primary) {
        GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
        beanDefinition.setFactoryBeanName(this.getClass().getName());
        beanDefinition.setPrimary(primary);
        beanDefinition.setAutowireCandidate(true);
        beanDefinition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);

        beanDefinition.setFactoryMethodName(factoryMethodName);
        ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
        constructorArgumentValues.addGenericArgumentValue(unit);
        beanDefinition.setConstructorArgumentValues(constructorArgumentValues);

        beanDefinition.getQualifier(unit + postfix);
        registry.registerBeanDefinition(unit + postfix, beanDefinition);
    }

    private AnnotationMetadata createAnnotationMetadata(String emfRef, String tmRef,
            String[] packages) {
        AnnotationMetadata obj = AnnotationMetadata.introspect(EnableJpaRepositoriesDiabled.class);
        return (AnnotationMetadata) Proxy.newProxyInstance(obj.getClass().getClassLoader(),
                new Class[] { AnnotationMetadata.class },
                new AnnotationMetadataInvocationHandler(obj, emfRef, tmRef, packages));
    }

    private static class AnnotationMetadataInvocationHandler implements InvocationHandler {
        private final AnnotationMetadata instance;
        private final String entityManagerFactoryRef;
        private final String transactionManagerRef;
        private final String[] basePackages;

        public AnnotationMetadataInvocationHandler(@NonNull AnnotationMetadata instance, String emfRef, String tmRef,
                String[] packages) {
            this.instance = instance;
            this.entityManagerFactoryRef = emfRef;
            this.transactionManagerRef = tmRef;
            this.basePackages = packages;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getAnnotationAttributes".equals(method.getName())
                    || "getAllAnnotationAttributes".equals(method.getName())) {
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) method.invoke(instance, args);
                r.put("entityManagerFactoryRef", entityManagerFactoryRef);
                r.put("transactionManagerRef", transactionManagerRef);
                r.put("basePackages", basePackages);
                return r;
            } else if ("getClassName".equals(method.getName())) {
                return EnableJpaRepositoriesDiabled.class.getName();
            }
            return method.invoke(instance, args);
        }
    }

    @Configuration
    static class WillRemovedTemporarilyBeans {
        static final String REMOVED_ENTITYMANAGER_FACTORYBUILDER = "willRemovedTempEntityManagerFactoryBuilder";
        static final String REMOVE_ENTITYMANAGER_FACTORY = "willRemovedTempEntityManagerFactory";
        static final String REMOVED_JPA_TRANSACTION_MANAGER1 = "willRemovedTempJPATransactionManager1";
        static final String REMOVED_JPA_TRANSACTION_MANAGER2 = "willRemovedTempJPATransactionManager2";

        @Bean(name = REMOVED_ENTITYMANAGER_FACTORYBUILDER)
        EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
            return null;
        }

        @Bean(name = REMOVE_ENTITYMANAGER_FACTORY)
        LocalContainerEntityManagerFactoryBean entityManagerFactory() {
            return null;
        }

        @Bean(name = REMOVED_JPA_TRANSACTION_MANAGER1)
        @ConditionalOnMissingBean(value = TransactionManager.class)
        PlatformTransactionManager transactionManager1() {
            return null;
        }

        @Bean(name = REMOVED_JPA_TRANSACTION_MANAGER2)
        @ConditionalOnBean(name = REMOVED_JPA_TRANSACTION_MANAGER1)
        PlatformTransactionManager transactionManager2() {
            return null;
        }
    }

    @EnableJpaRepositories
    @ConditionalOnMissingBean({ JpaRepositoryFactoryBean.class, JpaRepositoryConfigExtension.class })
    static class EnableJpaRepositoriesDiabled {

    }
}
