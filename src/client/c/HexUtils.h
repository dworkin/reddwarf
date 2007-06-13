/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

/*
 * Author: Ian Rose 
 * Date Created: June 13, 2007
 *
 * This file provides utility functions for converting between byte-arrays
 *  and hex-strings.
 */

#ifndef _HEXUTILS_H
#define _HEXUTILS_H 1

/*
 * INCLUDES
 */
#include <errno.h>
#include <stdint.h>
#include <stdio.h>   // included for sprintf()
#include <string.h>  // included for strlen()

/*
 * FUNCTION DECLARATIONS
 * (implementations are in ConversionUtils.c)
 */
int bytestohex(const uint8_t *ba, const int len, char *hexstr);
int hextobytes(const char *hexstr, uint8_t *ba);
int hextoi(char c);

#endif
