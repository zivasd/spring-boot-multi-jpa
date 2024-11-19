package multiple.jpa.sample.primary.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import multiple.jpa.sample.primary.entity.Person;

public interface PersonRepository extends JpaRepository<Person, Long> {
}
