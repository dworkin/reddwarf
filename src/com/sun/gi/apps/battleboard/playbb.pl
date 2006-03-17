#!/usr/bin/perl -w
#
# $Id: playbb.pl,v 1.1 2006/03/06 22:04:15 de153050 Exp de153050 $

# Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
# Clara, California 95054, U.S.A. All rights reserved.
# 
# Sun Microsystems, Inc. has intellectual property rights relating to
# technology embodied in the product that is described in this document.
# In particular, and without limitation, these intellectual property rights
# may include one or more of the U.S. patents listed at
# http://www.sun.com/patents and one or more additional patents or pending
# patent applications in the U.S. and in other countries.
# 
# U.S. Government Rights - Commercial software. Government users are subject
# to the Sun Microsystems, Inc. standard license agreement and applicable
# provisions of the FAR and its supplements.
# 
# This distribution may include materials developed by third parties.
# 
# Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
# trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
# 
# UNIX is a registered trademark in the U.S. and other countries, exclusively
# licensed through X/Open Company, Ltd.
# 
# Products covered by and information contained in this service manual are
# controlled by U.S. Export Control laws and may be subject to the export
# or import laws in other countries. Nuclear, missile, chemical biological
# weapons or nuclear maritime end uses or end users, whether direct or
# indirect, are strictly prohibited. Export or reexport to countries subject
# to U.S. embargo or to entities identified on U.S. export exclusion lists,
# including, but not limited to, the denied persons and specially designated
# nationals lists is strictly prohibited.
# 
# DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
# REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
# MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
# ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
# LEGALLY INVALID.
# 
# Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
# California 95054, Etats-Unis. Tous droits réservés.
# 
# Sun Microsystems, Inc. détient les droits de propriété intellectuels
# relatifs à la technologie incorporée dans le produit qui est décrit dans
# ce document. En particulier, et ce sans limitation, ces droits de
# propriété intellectuelle peuvent inclure un ou plus des brevets américains
# listés à l'adresse http://www.sun.com/patents et un ou les brevets
# supplémentaires ou les applications de brevet en attente aux Etats -
# Unis et dans les autres pays.
# 
# Cette distribution peut comprendre des composants développés par des
# tierces parties.
# 
# Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
# ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
# d'autres pays.
# 
# UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
# licenciée exlusivement par X/Open Company, Ltd.
# 
# see above Les produits qui font l'objet de ce manuel d'entretien et les
# informations qu'il contient sont regis par la legislation americaine en
# matiere de controle des exportations et peuvent etre soumis au droit
# d'autres pays dans le domaine des exportations et importations.
# Les utilisations finales, ou utilisateurs finaux, pour des armes
# nucleaires, des missiles, des armes biologiques et chimiques ou du
# nucleaire maritime, directement ou indirectement, sont strictement
# interdites. Les exportations ou reexportations vers des pays sous embargo
# des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
# d'exportation americaines, y compris, mais de maniere non exclusive, la
# liste de personnes qui font objet d'un ordre de ne pas participer, d'une
# facon directe ou indirecte, aux exportations des produits ou des services
# qui sont regi par la legislation americaine en matiere de controle des
# exportations et la liste de ressortissants specifiquement designes, sont
# rigoureusement interdites.
# 
# LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
# DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
# DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
# GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
# UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.

use FileHandle;
use IPC::Open2;

# NOTE:  if the BoardWidth and BoardHeight are not at least as large
# as the actual board, then there is no guarantee that the game will
# terminate.  It is not a problem if they are too large; the client
# (and server) will reject illegal moves.

$BoardWidth	= 4;
$BoardHeight	= 4;

$Props	= '-Dbattleboard.interactive=false';
$Class	= 'com.sun.gi.apps.battleboard.client.BattleBoardClient';

if (@ARGV < 3) {
    $x = @ARGV;
    print "$x usage: $0 UserName UserPasswd PlayerName\n";
    exit(1);
}

$UserName   = shift @ARGV;
$UserPasswd = shift @ARGV;
$PlayerName = shift @ARGV;

$Command	= "java -cp bin $Props $Class";

$GamesPlayed = 0;
$GamesWon    = 0;
$GamesLost   = 0;
$GamesError  = 0;


# Avoid zombie processes
$SIG{CHLD} = 'IGNORE';


# Play some games
for (;;) {
    $rc = play($Command, $UserName, $UserPasswd, $PlayerName);
    $GamesPlayed += 1;
    if ($rc == 1) {
	$GamesWon += 1;
    } elsif ($rc == 0) {
	$GamesLost += 1;
    } else {
	$GamesError += 1;
	if ($rc < -1) {
	    die "** Game returned $rc -- exiting\n";
	}
    }
}

sub play {
    my ($cmd, $user, $passwd, $player, @opponents) = @_;

    my $pid = open2(*Reader, *Writer, $cmd);

    while (my $line = <Reader>) {
	if (!($line =~ /\|/)) {
	    print "== $line";
	}

	$line =~ s/\s+$//;

	if ($line eq "User Name:") {
	    print Writer "$user\n";
	    flush Writer;
	}
	elsif ($line eq "Password:") {
	    print Writer "$passwd\n";
	    flush Writer;
	}
	elsif ($line eq "Enter your handle [$user]:") {
	    print Writer "$player\n";
	    flush Writer;
	    last;
	}
    }

    print "Starting game...\n";
    flush STDOUT;

    $rc = tryAll($player, $BoardWidth, $BoardHeight);
 
    close Reader;
    close Writer;

    return $rc;
}

sub tryAll {
    my ($player, $width, $height) = @_;

    for (my $x = 0; $x < $width; $x++) {
	for (my $y = 0; $y < $height; $y++) {

	    while (my $line = <Reader>) {
		if (!$line) {
		    last;
		}

		if ($line =~ /^\s+$/) {
			next;
		}
		if ($line =~ /^active/) {
		    next;
		}
		if ($line =~ /0\ \ 1\ \ 2/) {
		    next;
		}
		if ($line =~ /surviving\ /) {
		    next;
		}

		if (!($line =~ /\*/)) {
		    print "== $line";
		    flush STDOUT;
		}

		$line =~ s/\s+$//;

		if ($line eq "player x y, or pass") {
		    print Writer "$player $x $y\n";
		    flush Writer;
		    last;
		}
		elsif ($line eq "YOU WIN!") {
		    return 1;
		}
		elsif ($line =~ /WINS!$/) {
		    return 0;
		}
		elsif ($line =~ /is not in the game\.$/) {
		    return -1;
		}
		elsif ($line =~ /Connection refused/) {
		    return -2;
		}
	    }

	}
    }
}
