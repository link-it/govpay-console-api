package it.govpay.console.avviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import it.govpay.console.entity.IbanAccredito;
import it.govpay.console.entity.Tributo;
import it.govpay.console.repository.IbanAccreditoRepository;
import it.govpay.console.repository.TributoRepository;
import it.govpay.pendenze.entity.Pendenza;
import it.govpay.pendenze.entity.VocePendenza;

class IbanPostaleResolverTest {

    private final IbanAccreditoRepository ibanRepository = mock(IbanAccreditoRepository.class);
    private final TributoRepository tributoRepository = mock(TributoRepository.class);
    private final IbanPostaleResolver resolver = new IbanPostaleResolver(ibanRepository, tributoRepository);

    private static IbanAccredito iban(String codIban, boolean postale) {
        IbanAccredito i = new IbanAccredito();
        i.setCodIban(codIban);
        i.setPostale(postale);
        return i;
    }

    private static Pendenza pendenzaConVoce(VocePendenza... voci) {
        Pendenza p = new Pendenza();
        for (VocePendenza voce : voci) {
            p.addVocePendenza(voce);
        }
        return p;
    }

    private static VocePendenza voce(int indice) {
        VocePendenza voce = new VocePendenza();
        voce.setIndice(indice);
        return voce;
    }

    @Test
    void pendenzaDefinitaPrendeLIbanPostaleDallaVoce() {
        VocePendenza voce = voce(1);
        voce.setIdIbanAccredito(10L);
        when(ibanRepository.findById(10L)).thenReturn(Optional.of(iban("IT60X0542811101000000123456", true)));

        IbanAccredito scelto = resolver.risolvi(pendenzaConVoce(voce));

        assertThat(scelto.getCodIban()).isEqualTo("IT60X0542811101000000123456");
    }

    /** Pendenza a riferimento: l'IBAN sulla voce e' assente e si eredita dal tipo entrata del dominio. */
    @Test
    void pendenzaARiferimentoEreditaLIbanPostaleDalTributo() {
        VocePendenza voce = voce(1);
        voce.setIdTributo(5L);
        Tributo tributo = new Tributo();
        tributo.setIbanAccredito(iban("IT60X0542811101000000999999", true));
        when(tributoRepository.findById(5L)).thenReturn(Optional.of(tributo));

        IbanAccredito scelto = resolver.risolvi(pendenzaConVoce(voce));

        assertThat(scelto.getCodIban()).isEqualTo("IT60X0542811101000000999999");
    }

    @Test
    void ibanSullaVocePrevaleSuQuelloDelTributo() {
        VocePendenza voce = voce(1);
        voce.setIdIbanAccredito(10L);
        voce.setIdTributo(5L);
        when(ibanRepository.findById(10L)).thenReturn(Optional.of(iban("IT60X0542811101000000123456", true)));
        Tributo tributo = new Tributo();
        tributo.setIbanAccredito(iban("IT60X0542811101000000999999", true));
        when(tributoRepository.findById(5L)).thenReturn(Optional.of(tributo));

        IbanAccredito scelto = resolver.risolvi(pendenzaConVoce(voce));

        assertThat(scelto.getCodIban()).isEqualTo("IT60X0542811101000000123456");
    }

    /** L'appoggio si guarda solo se l'accredito non e' postale. */
    @Test
    void ibanDiAppoggioUsatoQuandoLAccreditoNonEPostale() {
        VocePendenza voce = voce(1);
        voce.setIdIbanAccredito(10L);
        voce.setIdIbanAppoggio(11L);
        when(ibanRepository.findById(10L)).thenReturn(Optional.of(iban("IT60X0542811101000000123456", false)));
        when(ibanRepository.findById(11L)).thenReturn(Optional.of(iban("IT60X0542811101000000777777", true)));

        IbanAccredito scelto = resolver.risolvi(pendenzaConVoce(voce));

        assertThat(scelto.getCodIban()).isEqualTo("IT60X0542811101000000777777");
    }

    @Test
    void nessunIbanPostaleRestituisceNull() {
        VocePendenza voce = voce(1);
        voce.setIdIbanAccredito(10L);
        when(ibanRepository.findById(10L)).thenReturn(Optional.of(iban("IT60X0542811101000000123456", false)));

        assertThat(resolver.risolvi(pendenzaConVoce(voce))).isNull();
    }

    @Test
    void pendenzaSenzaVociNonEPostale() {
        assertThat(resolver.risolvi(pendenzaConVoce())).isNull();
    }

    /** Conta la voce con indice minore, non la prima della lista. */
    @Test
    void siUsaLaVoceConIndiceMinore() {
        VocePendenza seconda = voce(2);
        seconda.setIdIbanAccredito(20L);
        VocePendenza prima = voce(1);
        prima.setIdIbanAccredito(10L);
        when(ibanRepository.findById(10L)).thenReturn(Optional.of(iban("IT60X0542811101000000123456", true)));
        when(ibanRepository.findById(20L)).thenReturn(Optional.of(iban("IT60X0542811101000000222222", true)));

        IbanAccredito scelto = resolver.risolvi(pendenzaConVoce(seconda, prima));

        assertThat(scelto.getCodIban()).isEqualTo("IT60X0542811101000000123456");
    }
}
