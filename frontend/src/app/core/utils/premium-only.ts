/**
 * HU16: un producto exclusivo Premium se enseña a todos, pero solo lo compra un cliente Premium.
 * El backend calcula `purchasable` para el cliente de la sesión; aquí solo se lee.
 */
export function isBlockedForCustomer(product: { premiumOnly?: boolean; purchasable?: boolean }): boolean {
  return product.premiumOnly === true && product.purchasable === false;
}
