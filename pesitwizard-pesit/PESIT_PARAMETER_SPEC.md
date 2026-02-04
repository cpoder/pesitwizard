# PESIT Parameter Specification

## Summary of Changes

The `ParameterIdentifier` and `ParameterGroupIdentifier` enums have been corrected based on the official PESIT E specification (pesit.md).

## ParameterIdentifier (PI) - Complete List from Spec

Based on section 4.7.2.2 of the PESIT specification:

| Code | Enum Name | Description |
|------|-----------|-------------|
| 1 | PI_01_CRC | CRC Usage |
| 2 | PI_02_DIAG | Diagnostic |
| 3 | PI_03_DEMANDEUR | Requestor Identification |
| 4 | PI_04_SERVEUR | Server Identification |
| 5 | PI_05_CONTROLE_ACCES | Access Control |
| 6 | PI_06_VERSION | Version Number |
| 7 | PI_07_SYNC_POINTS | Sync Points Option |
| 11 | PI_11_TYPE_FICHIER | File Type |
| 12 | PI_12_NOM_FICHIER | Filename |
| 13 | PI_13_ID_TRANSFERT | Transfer Identifier |
| 14 | PI_14_ATTRIBUTS_DEMANDES | Requested Attributes |
| 15 | PI_15_TRANSFERT_RELANCE | Transfer Restarted |
| 16 | PI_16_CODE_DONNEES | Data Code |
| 17 | PI_17_PRIORITE | Transfer Priority |
| 18 | PI_18_POINT_RELANCE | Restart Point |
| 19 | PI_19_CODE_FICHIER | File Code |
| 20 | PI_20_NUM_SYNC | Sync Point Number |
| 21 | PI_21_COMPRESSION | Compression |
| 22 | PI_22_TYPE_ACCES | Access Type |
| 23 | PI_23_RESYNC | Resynchronization |
| 25 | PI_25_TAILLE_MAX_ENTITE | Max Data Entity Size |
| 26 | PI_26_TIMEOUT | Timeout |
| 27 | PI_27_NB_OCTETS | Data Byte Count |
| 28 | PI_28_NB_ARTICLES | Article Count |
| 29 | PI_29_COMPLEMENT_DIAG | Diagnostic Complement |
| 31 | PI_31_FORMAT_ARTICLE | Article Format |
| 32 | PI_32_LONG_ARTICLE | Article Length |
| 33 | PI_33_ORG_FICHIER | File Organization |
| 34 | PI_34_SIGNATURE | Signature Handling |
| 36 | PI_36_SCEAU_SIT | SIT Seal |
| 37 | PI_37_LABEL_FICHIER | File Label |
| 38 | PI_38_LONG_CLE | Key Length |
| 39 | PI_39_DEPL_CLE | Key Offset in Record |
| 41 | PI_41_UNITE_RESERVATION | Reservation Unit |
| 42 | PI_42_MAX_RESERVATION | Max Space Reservation |
| 51 | PI_51_DATE_CREATION | Creation Date/Time |
| 52 | PI_52_DATE_EXTRACTION | Last Extraction Date/Time |
| 61 | PI_61_ID_CLIENT | Client Identifier |
| 62 | PI_62_ID_BANQUE | Bank Identifier |
| 63 | PI_63_ACCES_FICHIER | File Access Control |
| 64 | PI_64_DATE_SERVEUR | Server Date/Time |
| 71 | PI_71_TYPE_AUTH | Authentication Type |
| 72 | PI_72_ELEMS_AUTH | Authentication Elements |
| 73 | PI_73_TYPE_SCELLEMENT | Sealing Type |
| 74 | PI_74_ELEMS_SCELLEMENT | Sealing Elements |
| 75 | PI_75_TYPE_CHIFFR | Encryption Type |
| 76 | PI_76_ELEMS_CHIFFR | Encryption Elements |
| 77 | PI_77_TYPE_SIG | Signature Type |
| 78 | PI_78_SCEAU | Seal |
| 79 | PI_79_SIGNATURE | Signature |
| 80 | PI_80_ACCREDITATION | Accreditation |
| 81 | PI_81_ACCUSE_SIG | Signature Receipt Acknowledgment |
| 82 | PI_82_DEUTURE | Deuture |
| 83 | PI_83_ACCRED_2 | Second Accreditation |
| 91 | PI_91_MESSAGE | Message |
| 99 | PI_99_MESSAGE_LIBRE | Free Message |

## ParameterGroupIdentifier (PGI) - Complete List from Spec

Based on section 4.7.2.2 of the PESIT specification:

| Code | Enum Name | Description | Contains PIs |
|------|-----------|-------------|--------------|
| 9 | PGI_09_ID_FICHIER | File Identifier | PI 3, 4, 11, 12 |
| 30 | PGI_30_ATTR_LOGIQUES | Logical Attributes | PI 31, 32, 33, 34, 36, 37, 38, 39 |
| 40 | PGI_40_ATTR_PHYSIQUES | Physical Attributes | PI 41, 42 |
| 50 | PGI_50_ATTR_HISTORIQUES | Historical Attributes | PI 51, 52 |

## Key Differences from Previous Implementation

### Parameter Identifier Changes
- **Removed** invented parameters like `PI_10_ID_FICH`, `PI_24_UTILISATEUR`, `PI_25_MOT_PASSE`, `PI_26_PROFILE`, `PI_30_DATE_FICH`, `PI_40_VERSION_PROTOCOLE`, `PI_50_ACCES`, `PI_60_CLASSE_APPLI`
- **Renamed** many PIs to match the spec (e.g., `PI_11_NOM_FICH_LOCAL` → `PI_11_TYPE_FICHIER`)
- **Fixed codes** - PI 13 is Transfer ID (was incorrectly mapped to Final Filename)
- **Added missing PIs** like PI 1 (CRC), PI 61-64 (Client/Bank IDs), PI 71-83 (Security parameters), PI 91, PI 99

### Parameter Group Identifier Changes
- **Fixed codes**: The spec defines PGI 9, 30, 40, 50 (not 0x10, 0x20, 0x30, 0x40)
- **PGI 9**: File Identifier - contains PI 3 (Requestor), PI 4 (Server), PI 11 (File Type), PI 12 (Filename)
- **PGI 30**: Logical Attributes - file structure parameters
- **PGI 40**: Physical Attributes - space reservation
- **PGI 50**: Historical Attributes - dates

### Impact on Code
All code using the old parameter names will need to be updated to use the correct PESIT specification names and codes.

## Reference
Source: `pesit.md` section 4.7.2.2 "PGI and PI code list"
