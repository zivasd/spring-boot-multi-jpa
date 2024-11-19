package multiple.jpa.sample.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.persistence.EntityManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import multiple.jpa.sample.primary.entity.Person;

@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class TransactionTest {

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcOperations primaryOperations;

    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcOperations secondaryOperations;

    @Autowired
    @Qualifier("primaryEntityManagerFactory")
    private EntityManager primaryEntityManager;

    @BeforeAll
    void init() {
        primaryOperations.execute("create table t_person (id varchar(64), name varchar(64))");
        secondaryOperations.execute("create table t_user (id varchar(64), name varchar(64))");
    }

    @Test
    @Transactional
    @Rollback(true)
    @Order(1)
    void rollbackTestStep1() {
        Person person = new Person();
        person.setId(1L);
        person.setName("bob");
        primaryEntityManager.persist(person);
        person = primaryEntityManager.find(Person.class, 1L);
        assertEquals("bob", person.getName());
    }

    @Test
    @Order(2)
    void rollbackTestStep2() {
        Person person = primaryEntityManager.find(Person.class, 1L);
        assertNull(person);
    }

    @Test
    @Transactional("primaryTransactionManager")
    @Rollback(false)
    @Order(3)
    void commitTestStep1() {
        Person person = new Person();
        person.setId(1L);
        person.setName("jack");
        primaryEntityManager.persist(person);
        person = primaryEntityManager.find(Person.class, 1L);
        assertEquals("jack", person.getName());
    }

    @Test
    @Order(4)
    void commitTestStep2() {
        Person person = primaryEntityManager.find(Person.class, 1L);
        assertEquals("jack", person.getName());
    }
}
