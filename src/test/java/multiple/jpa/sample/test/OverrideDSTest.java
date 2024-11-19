package multiple.jpa.sample.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import multiple.jpa.sample.primary.dao.PersonRepository;
import multiple.jpa.sample.primary.entity.Person;
import multiple.jpa.sample.secondary.dao.UserRepository;
import multiple.jpa.sample.secondary.entity.User;

@SpringBootTest
@TestConfiguration()
@TestInstance(Lifecycle.PER_CLASS)
class OverrideDSTest {

    @TestConfiguration
    static class DataSouceBuilder {
        @Bean(name = "primaryDataSource")
        @Primary
        DataSource buildDataSource() {
            DataSourceProperties properties = new DataSourceProperties();
            properties.setUrl("jdbc:h2:mem:testdb");
            properties.setDriverClassName("org.h2.Driver");
            properties.setUsername("sa");
            return properties.initializeDataSourceBuilder().build();
        }

        @Bean(name = "primaryNamedParameterJdbcTemplate")
        @Primary
        NamedParameterJdbcTemplate buildNPJT(@Qualifier("primaryDataSource") DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean(name = "primaryJdbcTemplate")
        @Primary
        JdbcTemplate buildJdbcTemplete(
                @Qualifier("primaryNamedParameterJdbcTemplate") NamedParameterJdbcTemplate npjt) {
            return npjt.getJdbcTemplate();
        }

        @Bean(name = "secondaryDataSource")
        DataSource buildDataSource2() {
            DataSourceProperties properties = new DataSourceProperties();
            properties.setUrl("jdbc:h2:mem:testdb1");
            properties.setDriverClassName("org.h2.Driver");
            properties.setUsername("sa");
            return properties.initializeDataSourceBuilder().build();
        }

        @Bean(name = "secondaryNamedParameterJdbcTemplate")
        NamedParameterJdbcTemplate buildNPJT2(@Qualifier("secondaryDataSource") DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean(name = "secondaryJdbcTemplate")
        JdbcTemplate buildJdbcTemplete2(
                @Qualifier("secondaryNamedParameterJdbcTemplate") NamedParameterJdbcTemplate npjt) {
            return npjt.getJdbcTemplate();
        }
    }

    @Autowired
    @Qualifier("primaryJdbcTemplate")
    private JdbcOperations primaryOperations;

    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcOperations secondaryJdbcOperations;

    @Autowired
    private PersonRepository personRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeAll
    void init() {
        primaryOperations.execute("create table t_person (id varchar(64), name varchar(64))");
        primaryOperations.execute("insert into t_person (id, name) values('1','bob')");
        primaryOperations.execute("insert into t_person (id, name) values('2','tom')");

        secondaryJdbcOperations.execute("create table t_user (id varchar(64), name varchar(64))");
        secondaryJdbcOperations.execute("insert into t_user (id, name) values('1','李明')");
        secondaryJdbcOperations.execute("insert into t_user (id, name) values('2','赵三')");
    }

    @Test
    void testFirstEM() {
        Person person = personRepository.findById(1L).orElse(null);
        assertNotNull(person);
        assertEquals("bob", person.getName());

        List<Person> persons = personRepository.findAll();
        assertEquals(2, persons.size());
        List<String> names = persons.stream().map(Person::getName).collect(Collectors.toList());
        assertTrue(names.contains("bob"));
        assertTrue(names.contains("tom"));
    }

    @Test
    void testSecondEM() {
        User user = userRepository.findById(1L).orElse(null);
        assertNotNull(user);
        assertEquals("李明", user.getName());

        List<User> users = userRepository.findAll();
        assertEquals(2, users.size());
        List<String> names = users.stream().map(User::getName).collect(Collectors.toList());
        assertTrue(names.contains("李明"));
        assertTrue(names.contains("赵三"));
    }
}
