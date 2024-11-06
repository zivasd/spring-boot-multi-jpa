package io.github.zivasd.spring.boot.jpa.cfg;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotation.Adapt;
import org.springframework.core.annotation.MergedAnnotationSelectors;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.jpa.repository.config.JpaRepositoryConfigExtension;
import org.springframework.data.repository.config.AnnotationRepositoryConfigurationSource;
import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport;
import org.springframework.data.repository.config.RepositoryConfigurationDelegate;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.data.repository.config.RepositoryConfigurationUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

class MultipleJpaRepositoriesRegistrar extends RepositoryBeanDefinitionRegistrarSupport {
    private @SuppressWarnings("null") @NonNull ResourceLoader resourceLoader;
    private @SuppressWarnings("null") @NonNull Environment environment;

    @Override
    public void setResourceLoader(@NonNull ResourceLoader resourceLoader) {
        super.setResourceLoader(resourceLoader);
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(@NonNull Environment environment) {
        super.setEnvironment(environment);
        this.environment = environment;
    }

    @Override
    @NonNull
    protected Class<? extends Annotation> getAnnotation() {
        return EnableMultipleJpaRepositories.class;
    }

    @Override
    @NonNull
    protected RepositoryConfigurationExtension getExtension() {
        return new JpaRepositoryConfigExtension();
    }

    @Override
    public void registerBeanDefinitions(@NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry,
            @NonNull BeanNameGenerator generator) {
        Assert.notNull(metadata, "AnnotationMetadata must not be null");
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        Assert.notNull(resourceLoader, "ResourceLoader must not be null");

        if (metadata.getAnnotationAttributes(getAnnotation().getName()) != null) {
            registerSingle(metadata, registry, generator);
        } else if (metadata.getAnnotationAttributes(EnableMultipleJpaRepositoriesArray.class.getName()) != null) {
            registerMultiple(metadata, registry, generator);
        }
    }

    private void registerMultiple(AnnotationMetadata metadata, BeanDefinitionRegistry registry,
            BeanNameGenerator generator) {
        MergedAnnotation<Annotation> annotation = metadata.getAnnotations().get(
                EnableMultipleJpaRepositoriesArray.class.getName(),
                null, MergedAnnotationSelectors.firstDirectlyDeclared());

        MergedAnnotation<EnableMultipleJpaRepositories>[] values = annotation.getAnnotationArray("value",
                EnableMultipleJpaRepositories.class);
        for (MergedAnnotation<EnableMultipleJpaRepositories> value : values) {
            AnnotationRepositoryConfigurationSource configurationSource = new AnnotationRepositoryConfigurationSource(
                    createAnnotationMetadataDelegate(metadata, value),
                    EnableMultipleJpaRepositories.class, resourceLoader, environment, registry, generator);

            RepositoryConfigurationExtension extension = getExtension();
            RepositoryConfigurationUtils.exposeRegistration(extension, registry, configurationSource);

            RepositoryConfigurationDelegate delegate = new RepositoryConfigurationDelegate(configurationSource,
                    resourceLoader,
                    environment);

            delegate.registerRepositoriesIn(registry, extension);
        }
    }

    private void registerSingle(@NonNull AnnotationMetadata metadata, @NonNull BeanDefinitionRegistry registry,
            @NonNull BeanNameGenerator generator) {
        AnnotationRepositoryConfigurationSource configurationSource = new AnnotationRepositoryConfigurationSource(
                metadata,
                getAnnotation(), resourceLoader, environment, registry, generator);

        RepositoryConfigurationExtension extension = getExtension();
        RepositoryConfigurationUtils.exposeRegistration(extension, registry, configurationSource);

        RepositoryConfigurationDelegate delegate = new RepositoryConfigurationDelegate(configurationSource,
                resourceLoader,
                environment);

        delegate.registerRepositoriesIn(registry, extension);
    }

    private AnnotationMetadata createAnnotationMetadataDelegate(AnnotationMetadata metadata,
            MergedAnnotation<EnableMultipleJpaRepositories> annotation) {
        return (AnnotationMetadata) Proxy.newProxyInstance(metadata.getClass().getClassLoader(),
                new Class[] { AnnotationMetadata.class },
                new AnnotationMetadataDelegate(metadata, annotation));
    }

    static class AnnotationMetadataDelegate implements InvocationHandler {

        private final AnnotationMetadata instance;
        private final MergedAnnotation<EnableMultipleJpaRepositories> annotation;

        public AnnotationMetadataDelegate(AnnotationMetadata instance,
                MergedAnnotation<EnableMultipleJpaRepositories> annotation) {
            this.instance = instance;
            this.annotation = annotation;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getAnnotationAttributes".equals(method.getName())
                    || "getAllAnnotationAttributes".equals(method.getName())) {
                return annotation.asAnnotationAttributes(Adapt.ANNOTATION_TO_MAP);
            }
            return method.invoke(instance, args);
        }
    }
}
