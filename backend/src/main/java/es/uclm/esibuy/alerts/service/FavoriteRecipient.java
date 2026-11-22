package es.uclm.esibuy.alerts.service;

/** Un cliente que tiene un producto en favoritos y, por tanto, puede recibir alertas sobre él. */
public record FavoriteRecipient(String customerId, boolean premium) {
}
