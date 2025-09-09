package no.elixir.tsdapimock.endpoints.auth.basic;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientRepository extends CrudRepository<Client, String> {
  Optional<Client> findClientByIdAndPassword(String id, String password);
}
