# spring-boot-multi-jdbc

Provide automatic configuration of multiple jpa for Spring boot

# Getting Started

### Reference Documentation

### Guides

SpringBoot Configuration Sample

```yaml
spring:
  application:
    name: sample
  jpas:
    primary:
      data-source: primary
      base-packages:
        - com.example.entity
      repositories:
        base-packages:
          - com.example.repo.primary
      generate-ddl: false
      show-sql: true
      open-in-view: true
      database-platform: org.hibernate.dialect.DmDialect
      hibernate:
        ddl-auto: none
    secondary:
      data-source: secondary
      base-packages:
        - com.example.entity2
      repositories:
        base-packages:
          - com.example.repo.secondary
      show-sql: true
      hibernate:
        ddl-auto: none
      open-in-view: true
      database-platform: org.hibernate.dialect.DmDialect    
```
