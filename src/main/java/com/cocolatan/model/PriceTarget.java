package com.cocolatan.model;

/**
 * Which price(s) a bulk price update should adjust.
 */
public enum PriceTarget {
    /** Local sale price only. */
    LOCAL,
    /** PedidosYa price only. */
    PEDIDOSYA,
    /** Both local and PedidosYa prices. */
    BOTH
}
