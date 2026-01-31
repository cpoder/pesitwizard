/*
 * CX Virtual File Setup for PeSIT Wizard Integration Tests
 *
 * Creates a virtual file definition in Connect:Express.
 *
 * Usage: cx-setup-file <FILE_NAME> <DIRECTION> <RECEIVER> <SENDER> <PHYS_PATH> [RECORD_LEN]
 *
 * Compile: gcc -o cx-setup-file cx-setup-file.c -I$TOM_DIR/itom -L$TOM_DIR/itom -litom
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "d0b8z20.h"

#define SIZE_ZREQ sizeof(struct ZREQ_TOM)

int L0B8Z20(struct ZREQ_TOM *);

void print_usage(const char *prog) {
    fprintf(stderr, "Usage: %s <FILE_NAME> <DIRECTION> <RECEIVER> <SENDER> <PHYS_PATH> [RECORD_LEN] [FORMAT]\n", prog);
    fprintf(stderr, "\n");
    fprintf(stderr, "  FILE_NAME   : Symbolic file name (max 8 chars)\n");
    fprintf(stderr, "  DIRECTION   : R (receive) or T (transmit) or B (both)\n");
    fprintf(stderr, "  RECEIVER    : Receiver partner name (or $$ALL$$)\n");
    fprintf(stderr, "  SENDER      : Sender partner name (or $$ALL$$)\n");
    fprintf(stderr, "  PHYS_PATH   : Physical file path (max 64 chars)\n");
    fprintf(stderr, "  RECORD_LEN  : Record length (default: 04096)\n");
    fprintf(stderr, "  FORMAT      : TF=TextFixed, TV=TextVar, BF=BinFixed, BV=BinVar (default: BV)\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "Example: %s TESTFILE B '$$ALL$$' '$$ALL$$' /tmp/testfile.dat 04096 BV\n", prog);
}

int main(int argc, char *argv[]) {
    struct ZREQ_TOM *param;
    int status;
    char file_name[9] = {0};
    char direction[2] = {0};
    char receiver[9] = {0};
    char sender[9] = {0};
    char phys_path[MAX_PHYNAME_LENGTH + 1] = {0};
    char record_len[6] = "04096";
    char format[3] = "BV";

    if (argc < 6) {
        print_usage(argv[0]);
        return 1;
    }

    strncpy(file_name, argv[1], 8);
    strncpy(direction, argv[2], 1);
    strncpy(receiver, argv[3], 8);
    strncpy(sender, argv[4], 8);
    strncpy(phys_path, argv[5], MAX_PHYNAME_LENGTH);
    if (argc > 6) {
        strncpy(record_len, argv[6], 5);
    }
    if (argc > 7) {
        strncpy(format, argv[7], 2);
    }

    /* Pad with spaces */
    for (int i = strlen(file_name); i < 8; i++) file_name[i] = ' ';
    for (int i = strlen(receiver); i < 8; i++) receiver[i] = ' ';
    for (int i = strlen(sender); i < 8; i++) sender[i] = ' ';
    for (int i = strlen(record_len); i < 5; i++) record_len[i] = ' ';

    param = (struct ZREQ_TOM *)malloc(SIZE_ZREQ);
    memset((char *)param, ' ', SIZE_ZREQ);

    /* Request header */
    memcpy(param->zreq_tom_name, "tom1", 4);
    param->zreq_tom_func[0] = 'C';  /* Create */
    param->zreq_tom_tabn[0] = 'F';  /* File */
    memset(param->zreq_tom_reqn, ' ', 8);
    memset(param->zreq_tom_rtcf, 0x00, 4);

    /* File definition */
    memset(&param->uni.zreq_tom_fic, ' ', sizeof(struct fichier));
    memcpy(param->uni.zreq_tom_fic.nom_sym, file_name, 8);
    memcpy(param->uni.zreq_tom_fic.etat_init, "H", 1);       /* Hold (enabled) */
    memcpy(param->uni.zreq_tom_fic.direction, direction, 1);
    memcpy(param->uni.zreq_tom_fic.recepteur, receiver, 8);
    memcpy(param->uni.zreq_tom_fic.emetteur, sender, 8);
    memcpy(param->uni.zreq_tom_fic.priorite, "0", 1);        /* Normal priority */
    memcpy(param->uni.zreq_tom_fic.typ_def, "D", 1);         /* Dynamic */
    memcpy(param->uni.zreq_tom_fic.present, " ", 1);         /* No presentation table (binary) */
    memcpy(param->uni.zreq_tom_fic.nom_phy, phys_path, strlen(phys_path));
    memcpy(param->uni.zreq_tom_fic.format, format, 2);       /* Binary Variable by default */
    memcpy(param->uni.zreq_tom_fic.record, record_len, 5);   /* Record length */
    memcpy(param->uni.zreq_tom_fic.tab, "N", 1);             /* No optional table */
    memcpy(param->uni.zreq_tom_fic.alloc, "N", 1);           /* No pre-allocation */
    memcpy(param->uni.zreq_tom_fic.rule, "0", 1);            /* Rule 0 */
    memcpy(param->uni.zreq_tom_fic.stou, "N", 1);            /* No store unique */
    memcpy(param->uni.zreq_tom_fic.fa, "N", 1);              /* No file agent */

    status = L0B8Z20(param);

    if (status == 0) {
        printf("File %.8s created successfully\n", file_name);
        printf("  Direction:  %s\n", direction[0] == 'R' ? "Receive" : (direction[0] == 'T' ? "Transmit" : "Both"));
        printf("  Receiver:   %.8s\n", receiver);
        printf("  Sender:     %.8s\n", sender);
        printf("  Path:       %s\n", phys_path);
        printf("  Format:     %.2s\n", format);
        printf("  Record Len: %.5s\n", record_len);
    } else {
        fprintf(stderr, "Failed to create file %.8s\n", file_name);
        fprintf(stderr, "Return code: %.4s\n", param->zreq_tom_rtcf);
        free(param);
        return 2;
    }

    free(param);
    return 0;
}
