package com.ziva.spring.boot.jpa.cfg;

import java.util.List;

import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.jpa")
public class JpaPropertiesExt extends JpaProperties {
    private String dataSource;
    private List<String> basePackages;
    private HibernateProperties hibernate;
    private JpaRepositories repositories = new JpaRepositories();

    /**
     * @return the basePackages
     */
    public List<String> getBasePackages() {
        return basePackages;
    }

    /**
     * @param basePackages the basePackages to set
     */
    public void setBasePackages(List<String> basePackages) {
        this.basePackages = basePackages;
    }

    /**
     * @return the hibernate
     */
    public HibernateProperties getHibernate() {
        return hibernate;
    }

    /**
     * @param hibernate the hibernate to set
     */
    public void setHibernate(HibernateProperties hibernate) {
        this.hibernate = hibernate;
    }

    /**
     * @return the dataSource
     */
    public String getDataSource() {
        return dataSource;
    }

    /**
     * @param dataSource the dataSource to set
     */
    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @return the repositories
     */
    public JpaRepositories getRepositories() {
        return repositories;
    }

    /**
     * @param repositories the repositories to set
     */
    public void setRepositories(JpaRepositories repositories) {
        this.repositories = repositories;
    }

}
