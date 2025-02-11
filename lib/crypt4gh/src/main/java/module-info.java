module no.elixir.crypt4gh {
  requires com.rfksystems.blake2b;
  requires org.apache.commons.lang3;
  requires org.apache.commons.io;
  requires org.apache.commons.cli;
  requires org.slf4j;
  requires lombok;
  requires scrypt;

  exports no.elixir.crypt4gh.stream;
  exports no.elixir.crypt4gh.pojo;
  exports no.elixir.crypt4gh.util;
}
