package com.pesitwizard.fpdu;

import static com.pesitwizard.fpdu.ParameterIdentifier.*;

import lombok.Getter;

@Getter
public enum DiagnosticCode {
    D0_000(0x00, 0x00, "Succès"),
    D3_300(3, 300, "Congestion du système de communication local"),
    D3_301(3, 301, "Identification demandée inconnue"),
    D3_302(3, 302, "Demande non attachée à un SSAP"),
    D3_303(3, 303, "Congestion système de communication distant (trop de connexions)"),
    D3_304(3, 304, "Identification demandeur non autorisée (sécurité)"),
    D3_305(3, 305, "Echec d'une négociation : - SELECT"),
    D3_306(3, 306, "Echec d'une négociation : - RESYN"),
    D3_307(3, 307, "Echec d'une négociation : - SYNC"),
    D3_308(3, 308, "Numéro de version non supporté"),
    D3_309(3, 309, "Trop de connexions déjà en cours pour ce CT"),
    D3_310(3, 310, "Incident réseau"),
    D3_311(3, 311, "Erreur de protocole PeSIT distant"),
    D3_312(3, 312, "Fermeture du service demandée par l'utilisateur"),
    D3_313(3, 313, "Connexion rompue en fin d'intervalle d'inactivité TD"),
    D3_314(3, 314, "Connexion inutilisée rompue pour accueillir une nouvelle connexion"),
    D3_315(3, 315, "Echec de négociation"),
    D3_316(3, 316, "Connexion rompue à cause d'une commande de l'administration"),
    D3_317(3, 317, "Echéance de temporisation"),
    D3_318(3, 318, "PI obligatoire absent ou contenu illicite d'un PI"),
    D3_319(3, 319, "Nombre d'octets ou d'articles incorrects"),
    D3_320(3, 320, "Nombre excessif de resynchronisations pour un transfert"),
    D3_321(3, 321, "Appeler le numéro de secours"),
    D3_322(3, 322, "Rappeler ultérieurement"),
    D3_399(3, 399, "Autres"),
    D2_200(2, 200, "Caractéristiques du fichier insuffisantes"),
    D2_201(2, 201, "Ressources système provisoirement insuffisantes"),
    D2_202(2, 202, "Ressources utilisateur provisoirement insuffisantes"),
    D2_203(2, 203, "Transfert non prioritaire"),
    D2_204(2, 204, "Fichier existe déjà"),
    D2_205(2, 205, "Fichier inexistant"),
    D2_206(2, 206, "Réception du fichier causera un dépassement du quota disque"),
    D2_207(2, 207, "Fichier occupé"),
    D2_208(2, 208, "Fichier non vieux (antérieur à J-2 au sens SIT)"),
    D2_209(2, 209, "Message de ce type non accepté sur l'installation référencée"),
    D2_210(2, 210, "Echec de négociation de contexte de présentation"),
    D2_211(2, 211, "Ouverture de fichier impossible"),
    D2_212(2, 212, "Impossibilité fermeture normale fichier"),
    D2_213(2, 213, "Erreur d'entrée/sortie bloquante"),
    D2_214(2, 214, "Echec de négociation sur point de relance"),
    D2_215(2, 215, "Erreur propre au système"),
    D2_216(2, 216, "Arrêt prématuré volontaire"),
    D2_217(2, 217, "Trop de points de synchronisation sans acquittement"),
    D2_218(2, 218, "Resynchronisation impossible"),
    D2_219(2, 219, "Espace fichier épuisé"),
    D2_220(2, 220, "Article de longueur supérieure à celle attendue"),
    D2_221(2, 221, "Echéance du délai de fin de transmission"),
    D2_222(2, 222, "Trop de données sans point de synchronisation"),
    D2_223(2, 223, "Fin de transfert anormal"),
    D2_224(2, 224, "La taille du fichier transmis est plus importante que celle annoncée dans le F.CREATE"),
    D2_225(2, 225,
            "Congestion de l'application station : le fichier a bien été reçu mais SCRS n'a pu le donner à l'application station"),
    D2_226(2, 226, "Refus de transfert"),
    D2_227(2, 227, "Reprise impossible - transfert non repreniable"),
    D2_228(2, 228, "Reprise impossible - point de synchronisation inconnu"),
    D2_229(2, 229, "Reprise impossible - fichier modifié"),
    D2_230(2, 230, "Reprise impossible - délai dépassé"),
    D2_043(2, 43, "Reprise impossible"),
    D2_299(2, 299, "Autres"),
    D1_100(1, 100, "Erreur de transmission");

    /**
     * Check if this diagnostic indicates a restart/resume is not supported.
     */
    public boolean isRestartNotSupported() {
        return this == D2_214 || this == D2_218 || this == D2_227
                || this == D2_228 || this == D2_229 || this == D2_230 || this == D2_043;
    }

    private final byte code;
    private final short reason;
    private final String message;

    DiagnosticCode(int code, int reason, String message) {
        this.code = (byte) (code & 0xFF);
        this.reason = (short) (reason & 0xFFFF);
        this.message = message;
    }

    public static DiagnosticCode fromParameterValue(ParameterValue pi2) {
        if (pi2.getParameter() != PI_02_DIAG) {
            throw new IllegalArgumentException("Expected PI_02_DIAG, got " + pi2.getParameter());
        }
        int code = (pi2.getValue()[0] & 0xFF);
        int reason = (pi2.getValue()[1] & 0xFF) << 8 | pi2.getValue()[2] & 0xFF;
        for (DiagnosticCode diagnosticCode : DiagnosticCode.values()) {
            if (diagnosticCode.getCode() == code && diagnosticCode.getReason() == reason) {
                return diagnosticCode;
            }
        }
        return null;
    }

    /**
     * Convert this diagnostic code to a 3-byte array for PI_02_DIAG parameter.
     * Format: [code][reason_high][reason_low]
     */
    public byte[] toBytes() {
        return new byte[] {
                code,
                (byte) ((reason >> 8) & 0xFF),
                (byte) (reason & 0xFF)
        };
    }
}
