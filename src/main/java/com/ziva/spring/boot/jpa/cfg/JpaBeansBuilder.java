package com.ziva.spring.boot.jpa.cfg;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryBuilderCustomizer;
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
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryConfigurationDelegate;
import org.springframework.lang.NonNull;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceUnitManager;
import org.springframework.orm.jpa.vendor.AbstractJpaVendorAdapter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component("com.ziva.spring.boot.jpa.cfg.JpaBeansBuilder")
@ComponentScan({ "com.ziva.spring.boot.jpa.cfg" })
public class JpaBeansBuilder
        implements BeanDefinitionRegistryPostProcessor, EnvironmentAware, ApplicationContextAware, ResourceLoaderAware {

    private Map<String, JpaPropertiesExt> jpaes;

    private Environment environment;
    private ResourceLoader resourceLoader;
    private ApplicationContext applicationContext;

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        Binder binder = Binder.get(environment);
        jpaes = binder.bind("spring.jpas", Bindable.mapOf(String.class, JpaPropertiesExt.class)).get();

        if (jpaes == null || jpaes.size() == 0)
            return;

        registry.removeBeanDefinition("WillRemovedTempEntityManagerFactoryBuilder");
        registry.removeBeanDefinition("WillRemovedTempEntityManagerFactory");
        registry.removeBeanDefinition("WillRemovedTempTransactionManager");

        boolean primary = true;
        for (Map.Entry<String, JpaPropertiesExt> entry : jpaes.entrySet()) {
            registerEntityManagerFactoryBuild(registry, entry.getKey(), primary);
            registerEntityManagerFactory(registry, entry.getKey(), primary);
            registerTransactionManager(registry, entry.getKey(), primary);
            primary = false;
        }

        for (Map.Entry<String, JpaPropertiesExt> entry : jpaes.entrySet()) {
            String unit = entry.getKey();
            AnnotationMetadata metadata = createAnnotationMetadata(unit + "EntityManagerFactory",
                    unit + "TransactionManager", entry.getValue().getRepositories().getBasePackages());

            AnnotationRepositoryConfigurationSource configurationSource = new AnnotationRepositoryConfigurationSource(
                    metadata, EnableJpaRepositories.class,
                    JpaBeansBuilder.this.resourceLoader, JpaBeansBuilder.this.environment,
                    registry,
                    ConfigurationClassPostProcessor.IMPORT_BEAN_NAME_GENERATOR);
            RepositoryConfigurationDelegate delegate = new RepositoryConfigurationDelegate(configurationSource,
                    resourceLoader,
                    environment);
            delegate.registerRepositoriesIn(registry, new JpaRepositoryConfigExtension());
        }
    }

    private void registerEntityManagerFactoryBuild(@NonNull BeanDefinitionRegistry registry, String unitName,
            boolean primary) {
        registerBean(registry, "entityManagerFactoryBuilder", unitName, "EntityManagerFactoryBuilder", primary);
    }

    private void registerEntityManagerFactory(@NonNull BeanDefinitionRegistry registry, String unitName,
            boolean primary) {
        registerBean(registry, "entityManagerFactory", unitName, "EntityManagerFactory", primary);
    }

    private void registerTransactionManager(@NonNull BeanDefinitionRegistry registry, String unitName,
            boolean primary) {
        registerBean(registry, "transactionManager", unitName, "TransactionManager", primary);
    }

    public EntityManagerFactoryBuilder entityManagerFactoryBuilder(JpaVendorAdapter jpaVendorAdapter,
            ObjectProvider<PersistenceUnitManager> persistenceUnitManager,
            ObjectProvider<EntityManagerFactoryBuilderCustomizer> customizers, String unit) {

        JpaPropertiesExt jpaProperties = this.jpaes.get(unit);
        AbstractJpaVendorAdapter adapter = (AbstractJpaVendorAdapter) jpaVendorAdapter;
        adapter.setDatabasePlatform(jpaProperties.getDatabasePlatform());
        adapter.setGenerateDdl(jpaProperties.isGenerateDdl());
        adapter.setShowSql(jpaProperties.isShowSql());
        EntityManagerFactoryBuilder builder = new EntityManagerFactoryBuilder(jpaVendorAdapter,
                jpaProperties.getProperties(), persistenceUnitManager.getIfAvailable());
        customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
        return builder;
    }

    public LocalContainerEntityManagerFactoryBean entityManagerFactory(@NonNull String unit) {
        JpaPropertiesExt jpa = jpaes.get(unit);
        EntityManagerFactoryBuilder builder = this.applicationContext.getBean(unit + "EntityManagerFactoryBuilder",
                EntityManagerFactoryBuilder.class);
        DataSource dataSourceObject = this.applicationContext.getBean(jpa.getDataSource() + "DataSource",
                DataSource.class);
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
                .getBean(unit + "EntityManagerFactory", EntityManagerFactory.class);
        return new JpaTransactionManager(entityManagerFactoryBean);
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
        AnnotationMetadata obj = AnnotationMetadata.introspect(EnableJpaRepositoriesI.class);
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
            }
            return method.invoke(instance, args);
        }
    }

    @Configuration
    static class WillRemovedTemporarilyBeans {

        @Bean(name = "WillRemovedTempEntityManagerFactoryBuilder")
        EntityManagerFactoryBuilder entityManagerFactoryBuilder(JpaVendorAdapter jpaVendorAdapter,
                ObjectProvider<PersistenceUnitManager> persistenceUnitManager,
                ObjectProvider<EntityManagerFactoryBuilderCustomizer> customizers) {
            return null;
        }

        @Bean(name = "WillRemovedTempEntityManagerFactory")
        LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder) {
            return null;
        }

        @Bean(name = "WillRemovedTempTransactionManager")
        PlatformTransactionManager transactionManager(EntityManagerFactoryBuilder builder) {
            return null;
        }
    }

    @EnableJpaRepositories
    static class EnableJpaRepositoriesI {

    }
}
