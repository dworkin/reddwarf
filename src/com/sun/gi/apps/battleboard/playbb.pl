#!/usr/bin/perl -w
#
# $Id: playbb.pl,v 1.1 2006/03/06 22:04:15 de153050 Exp de153050 $
#
# Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
# Clara, California 95054, U.S.A. All rights reserved.
# 
# Sun Microsystems, Inc. has intellectual property rights relating to
# technology embodied in the product that is described in this
# document. In particular, and without limitation, these intellectual
# property rights may include one or more of the U.S. patents listed at
# http://www.sun.com/patents and one or more additional patents or
# pending patent applications in the U.S. and in other countries.
# 
# U.S. Government Rights - Commercial software. Government users are
# subject to the Sun Microsystems, Inc. standard license agreement and
# applicable provisions of the FAR and its supplements.
# 
# Use is subject to license terms.
# 
# This distribution may include materials developed by third parties.
# 
# Sun, Sun Microsystems, the Sun logo and Java are trademarks or
# registered trademarks of Sun Microsystems, Inc. in the U.S. and other
# countries.
# 
# This product is covered and controlled by U.S. Export Control laws
# and may be subject to the export or import laws in other countries.
# Nuclear, missile, chemical biological weapons or nuclear maritime end
# uses or end users, whether direct or indirect, are strictly
# prohibited. Export or reexport to countries subject to U.S. embargo
# or to entities identified on U.S. export exclusion lists, including,
# but not limited to, the denied persons and specially designated
# nationals lists is strictly prohibited.
# 
# Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
# Clara, California 95054, Etats-Unis. Tous droits réservés.
# 
# Sun Microsystems, Inc. détient les droits de propriété intellectuels
# relatifs à la technologie incorporée dans le produit qui est décrit
# dans ce document. En particulier, et ce sans limitation, ces droits
# de propriété intellectuelle peuvent inclure un ou plus des brevets
# américains listés à l'adresse http://www.sun.com/patents et un ou les
# brevets supplémentaires ou les applications de brevet en attente aux
# Etats - Unis et dans les autres pays.
# 
# L'utilisation est soumise aux termes de la Licence.
# 
# Cette distribution peut comprendre des composants développés par des
# tierces parties.
# 
# Sun, Sun Microsystems, le logo Sun et Java sont des marques de
# fabrique ou des marques déposées de Sun Microsystems, Inc. aux
# Etats-Unis et dans d'autres pays.
# 
# Ce produit est soumis à la législation américaine en matière de
# contrôle des exportations et peut être soumis à la règlementation en
# vigueur dans d'autres pays dans le domaine des exportations et
# importations. Les utilisations, ou utilisateurs finaux, pour des
# armes nucléaires,des missiles, des armes biologiques et chimiques ou
# du nucléaire maritime, directement ou indirectement, sont strictement
# interdites. Les exportations ou réexportations vers les pays sous
# embargo américain, ou vers des entités figurant sur les listes
# d'exclusion d'exportation américaines, y compris, mais de manière non
# exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
# participer, d'une façon directe ou indirecte, aux exportations des
# produits ou des services qui sont régis par la législation américaine
# en matière de contrôle des exportations et la liste de ressortissants
# spécifiquement désignés, sont rigoureusement interdites.

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

    for (my $x = 0; $x < $BoardWidth; $x++) {
	for (my $y = 0; $y < $BoardHeight; $y++) {

	    while (my $line = <Reader>) {
		if ($line =~ /^\s+$/) {
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
		    close Reader;
		    close Writer;
		    wait;
		    return 1;
		}
		elsif ($line =~ /WINS!$/) {
		    close Reader;
		    close Writer;
		    wait;
		    return 0;
		}
		elsif ($line =~ /is not in the game\.$/) {
		    kill "TERM", $pid;
		    close Reader;
		    close Writer;
		    wait;
		    return -1;
		}
		elsif ($line =~ /Connection refused/) {
		    close Reader;
		    close Writer;
		    wait;
		    return -2;
		}
	    }
	}
    }
}
