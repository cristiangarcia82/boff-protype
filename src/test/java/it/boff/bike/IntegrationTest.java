package it.boff.bike;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configurazione unica per i test di integrazione: tutte le classi condividono
 * lo stesso contesto Spring e lo stesso PostgreSQL in Docker (avvio una sola volta).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
        "boff.titolare.email=Titolare@Negozio.IT",
        "boff.titolare.password=segreta123",
        "boff.demo=true",
        "boff.demo-storico=false",
        "spring.docker.compose.enabled=false"
})
@Import(TestcontainersConfiguration.class)
public @interface IntegrationTest {
}
