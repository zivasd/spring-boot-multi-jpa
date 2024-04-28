package io.github.zivasd.spring.boot.jpa.cfg;

public class JpaRepositories {
    private String[] basePackages = new String[] {};

    /**
     * @return the basePackages
     */
    public String[] getBasePackages() {
        return basePackages;
    }

    /**
     * @param basePackages the basePackages to set
     */
    public void setBasePackages(String[] basePackages) {
        this.basePackages = basePackages;
    }
}
