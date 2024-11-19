package multiple.jpa.sample.secondary.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import multiple.jpa.sample.secondary.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
