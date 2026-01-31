/*
 * CX Partner Setup for PeSIT Wizard Integration Tests
 *
 * Creates a partner definition in Connect:Express to allow
 * PeSIT Wizard server to receive connections.
 *
 * Usage: cx-setup-partner <PARTNER_NAME> <TCP_HOST> <TCP_PORT> <DPCSID> [PASSWD]
 *
 * Compile: gcc -o cx-setup-partner cx-setup-partner.c -I$TOM_DIR/itom -L$TOM_DIR/itom -litom
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "d0b8z20.h"

#define SIZE_ZREQ sizeof(struct ZREQ_TOM)

int L0B8Z20(struct ZREQ_TOM *);

void print_usage(const char *prog) {
    fprintf(stderr, "Usage: %s <PARTNER_NAME> <TCP_HOST> <TCP_PORT> <DPCSID> [PASSWD]\n", prog);
    fprintf(stderr, "\n");
    fprintf(stderr, "  PARTNER_NAME  : Symbolic partner name (max 8 chars)\n");
    fprintf(stderr, "  TCP_HOST      : Target hostname or IP\n");
    fprintf(stderr, "  TCP_PORT      : Target port (5 digits)\n");
    fprintf(stderr, "  DPCSID        : PeSIT server ID (max 8 chars)\n");
    fprintf(stderr, "  PASSWD        : Password (optional, max 8 chars)\n");
    fprintf(stderr, "\n");
    fprintf(stderr, "Example: %s PWSERVER localhost 05001 PWSRV01 PASSWD\n", prog);
}

int main(int argc, char *argv[]) {
    struct ZREQ_TOM *param;
    int status;
    char partner_name[9] = {0};
    char tcp_host[65] = {0};
    char tcp_port[6] = {0};
    char dpcsid[9] = {0};
    char passwd[9] = {0};

    if (argc < 5) {
        print_usage(argv[0]);
        return 1;
    }

    strncpy(partner_name, argv[1], 8);
    strncpy(tcp_host, argv[2], 64);
    strncpy(tcp_port, argv[3], 5);
    strncpy(dpcsid, argv[4], 8);
    if (argc > 5) {
        strncpy(passwd, argv[5], 8);
    }

    /* Pad with spaces */
    for (int i = strlen(partner_name); i < 8; i++) partner_name[i] = ' ';
    for (int i = strlen(tcp_port); i < 5; i++) tcp_port[i] = ' ';
    for (int i = strlen(dpcsid); i < 8; i++) dpcsid[i] = ' ';
    for (int i = strlen(passwd); i < 8; i++) passwd[i] = ' ';

    param = (struct ZREQ_TOM *)malloc(SIZE_ZREQ);
    memset((char *)param, ' ', SIZE_ZREQ);

    /* Request header */
    memcpy(param->zreq_tom_name, "tom1", 4);
    param->zreq_tom_func[0] = 'C';  /* Create */
    param->zreq_tom_tabn[0] = 'P';  /* Partner */
    memset(param->zreq_tom_reqn, ' ', 8);
    memset(param->zreq_tom_rtcf, 0x00, 4);

    /* Partner definition */
    memset(&param->uni.zreq_tom_part, ' ', sizeof(struct partenaire));
    memcpy(param->uni.zreq_tom_part.nom_sym, partner_name, 8);
    memcpy(param->uni.zreq_tom_part.passwd, passwd, 8);
    memcpy(param->uni.zreq_tom_part.etat_init, "E", 1);      /* Enabled */
    memcpy(param->uni.zreq_tom_part.nature, "T", 1);         /* TCP/IP */
    memcpy(param->uni.zreq_tom_part.num_prot, "3", 1);       /* PeSIT */
    memcpy(param->uni.zreq_tom_part.tab_sess, "1", 1);       /* Session table 1 */
    memcpy(param->uni.zreq_tom_part.tab_pres, " ", 1);       /* No presentation table (binary) */
    memcpy(param->uni.zreq_tom_part.nb_liai, "10", 2);       /* Max sessions */
    memcpy(param->uni.zreq_tom_part.nb_liai_in, "05", 2);    /* Max incoming */
    memcpy(param->uni.zreq_tom_part.nb_liai_out, "05", 2);   /* Max outgoing */
    memcpy(param->uni.zreq_tom_part.typ_lia, "M", 1);        /* Mixed */
    memcpy(param->uni.zreq_tom_part.dpcsid, dpcsid, 8);      /* Server ID */
    memcpy(param->uni.zreq_tom_part.dpcpsw, passwd, 8);      /* Password */
    memcpy(param->uni.zreq_tom_part.tcp_host, tcp_host, strlen(tcp_host));
    memcpy(param->uni.zreq_tom_part.tcp_port, tcp_port, 5);

    status = L0B8Z20(param);

    if (status == 0) {
        printf("Partner %.8s created successfully\n", partner_name);
        printf("  TCP Host: %s\n", tcp_host);
        printf("  TCP Port: %.5s\n", tcp_port);
        printf("  DPCSID:   %.8s\n", dpcsid);
    } else {
        fprintf(stderr, "Failed to create partner %.8s\n", partner_name);
        fprintf(stderr, "Return code: %.4s\n", param->zreq_tom_rtcf);
        free(param);
        return 2;
    }

    free(param);
    return 0;
}
