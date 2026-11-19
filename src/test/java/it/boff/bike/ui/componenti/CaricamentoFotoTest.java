package it.boff.bike.ui.componenti;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CaricamentoFotoTest {

    @Test
    void ilServerRifiutaFileTroppoGrandiODiDimensioneIgnota() {
        assertThat(CaricamentoFoto.dimensioneAmmessa(5_000_000)).isTrue();
        assertThat(CaricamentoFoto.dimensioneAmmessa(8 * 1024 * 1024)).isTrue();
        assertThat(CaricamentoFoto.dimensioneAmmessa(8 * 1024 * 1024 + 1)).isFalse();
        assertThat(CaricamentoFoto.dimensioneAmmessa(-1)).as("dimensione non dichiarata").isFalse();
        assertThat(CaricamentoFoto.dimensioneAmmessa(0)).isFalse();
    }
}
