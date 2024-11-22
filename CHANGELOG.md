# Changelog

-------------------------------------------------------------------------------------------------------------
## 1.0.5

* create TransactionTemplate
* 在使用多数据仓库时，须设置spring.data.jpa.repositories.enabled=false


## 1.0.4(2024-11-06)

* 允许不配置repositories,可以在程序中使用@EnableJpaRepositories
* 支持Repeatable Annotation EnableMultipleJpaRepositories

## 1.0.3(2024-04-28)

* 修改包名，适配github.
* 增加日志输出
* 修改DataSource查找规则

## 1.0.2(2024-04-27)

### 特性

* 读取SpringBoot配置文件，自动注册EntityManagerFactoryBuilder、EntityManagerFactory、TransactionManager
* 支持为各JPA指定DataSource bean
