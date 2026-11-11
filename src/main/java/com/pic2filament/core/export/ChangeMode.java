package com.pic2filament.core.export;

/** Cómo se hace el cambio de filamento en el proyecto 3MF. */
public enum ChangeMode {

    /**
     * Cambio de herramienta: el laminador cambia a la ranura del filamento (AMS de Bambu, ACE Pro de
     * Anycubic). La vista previa del laminador muestra los colores.
     */
    AUTOMATIC,

    /** Pausa en la capa indicada (en la Kobra 3 es un M600) para cambiar el filamento a mano. */
    MANUAL
}
