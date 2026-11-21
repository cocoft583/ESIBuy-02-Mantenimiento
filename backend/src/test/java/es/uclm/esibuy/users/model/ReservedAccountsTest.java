package es.uclm.esibuy.users.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReservedAccountsTest {

    @Test
    @DisplayName("HU18-CP01: el identificador del cliente eliminado está reservado")
    void deletedCustomerIdentifierIsReserved() {
        assertTrue(ReservedAccounts.isReserved(ReservedAccounts.DELETED_CUSTOMER_ID));
    }

    @Test
    @DisplayName("HU18-CP02: cualquier correo del dominio esibuy.local está reservado")
    void anyEmailOfTheReservedDomainIsReserved() {
        assertTrue(ReservedAccounts.isReserved("cliente.eliminado@esibuy.local"));
        assertTrue(ReservedAccounts.isReserved("otra.cuenta@esibuy.local"));
    }

    @Test
    @DisplayName("HU18-CP03: la reserva ignora mayúsculas y espacios")
    void reservationIgnoresCaseAndSurroundingSpaces() {
        assertTrue(ReservedAccounts.isReserved("  Cliente-Eliminado "));
        assertTrue(ReservedAccounts.isReserved(" ADMIN@ESIBUY.LOCAL "));
    }

    @Test
    @DisplayName("HU18-CP04: un correo normal no está reservado")
    void anOrdinaryEmailIsNotReserved() {
        assertFalse(ReservedAccounts.isReserved("ana@example.com"));
        assertFalse(ReservedAccounts.isReserved("ana@esibuy.com"));
        assertFalse(ReservedAccounts.isReserved("ana@esibuy.local.example.com"));
    }

    @Test
    @DisplayName("HU18-CP05: sin correo no hay nada reservado")
    void aMissingEmailIsNotReserved() {
        assertFalse(ReservedAccounts.isReserved(null));
        assertFalse(ReservedAccounts.isReserved("   "));
    }

    @Test
    @DisplayName("HU18-CP06: el usuario técnico se reconoce por el identificador nuevo y por el correo antiguo")
    void deletedCustomerIsRecognisedByTheNewIdentifierAndTheLegacyEmail() {
        assertTrue(ReservedAccounts.isDeletedCustomer("cliente-eliminado"));
        assertTrue(ReservedAccounts.isDeletedCustomer("Cliente.Eliminado@esibuy.local"));
        assertFalse(ReservedAccounts.isDeletedCustomer("otra.cuenta@esibuy.local"));
        assertFalse(ReservedAccounts.isDeletedCustomer(null));
    }
}
