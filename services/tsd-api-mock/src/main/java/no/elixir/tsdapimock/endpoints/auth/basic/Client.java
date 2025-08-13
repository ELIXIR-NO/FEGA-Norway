package no.elixir.tsdapimock.endpoints.auth.basic;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  private String name;
  private String email;
  private String userName;
  private String password;

  @Column(length = 1000)
  private String confirmationToken;
}
