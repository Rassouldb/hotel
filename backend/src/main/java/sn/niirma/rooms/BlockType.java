package sn.niirma.rooms;

/**
 * Type de blocage (§9.4). {@code OUT_OF_ORDER} retire la chambre de la capacité ; {@code MAINTENANCE}
 * et {@code HOUSE_USE} la laissent dans la capacité mais hors vente.
 */
public enum BlockType { MAINTENANCE, OUT_OF_ORDER, HOUSE_USE }
