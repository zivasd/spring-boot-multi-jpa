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

import multiple.jpa.sample.secondary.entity.User;

@SpringBootTest
@TestInstance(Lifecycle.PER_CLASS)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class SecondTransactionTest {

    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcOperations secondaryOperations;

    @Autowired
    @Qualifier("secondaryEntityManagerFactory")
    private EntityManager secondaryEntityManager;

    @BeforeAll
    void init() {
        secondaryOperations.execute("create table t_user (id varchar(64), name varchar(64))");
    }

    @Test
    @Transactional("secondaryTransactionManager")
    @Rollback(true)
    @Order(1)
    void rollbackTestStep1() {
        User person = new User();
        person.setId(1L);
        person.setName("bob");
        secondaryEntityManager.persist(person);
        person = secondaryEntityManager.find(User.class, 1L);
        assertEquals("bob", person.getName());
    }

    @Test
    @Order(2)
    void rollbackTestStep2() {
        User person = secondaryEntityManager.find(User.class, 1L);
        assertNull(person);
    }

    @Test
    @Transactional("secondaryTransactionManager")
    @Rollback(false)
    @Order(3)
    void commitTestStep1() {
        User person = new User();
        person.setId(1L);
        person.setName("jack");
        secondaryEntityManager.persist(person);
        person = secondaryEntityManager.find(User.class, 1L);
        assertEquals("jack", person.getName());
    }

    @Test
    @Order(4)
    void commitTestStep2() {
        User person = secondaryEntityManager.find(User.class, 1L);
        assertEquals("jack", person.getName());
    }
}
