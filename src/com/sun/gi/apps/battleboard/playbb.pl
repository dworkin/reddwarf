#!/usr/bin/perl -w
#
# $Id: playbb.pl,v 1.1 2006/03/06 22:04:15 de153050 Exp de153050 $

use FileHandle;
use IPC::Open2;

$BoardWidth	= 2;
$BoardHeight	= 2;

$Props	= '-Dbattleboard.interactive=false';
$Class	= 'com.sun.gi.apps.battleboard.client.BattleBoardClient';

if (@ARGV < 3) {
    $x = @ARGV;
    print "$x usage: $0 UserName UserPasswd PlayerName [opponents]\n";
    exit(1);
}

$UserName   = shift @ARGV;
$UserPasswd = shift @ARGV;
$PlayerName = shift @ARGV;
@opponents  = @ARGV;

$Command	= "java -cp bin $Props $Class";

$GamesPlayed = 0;
$GamesWon    = 0;
$GamesLost   = 0;
$GamesError  = 0;

for (;;) {
    $rc = play($Command, $UserName, $UserPasswd, $PlayerName, @opponents);
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

    print Writer "$user\n";
    print Writer "$passwd\n";
    print Writer "$player\n";
    flush STDOUT;

    while (my $line = <Reader>) {
	if (!($line =~ /\|/)) {
	    print "== $line";
	}

	$line =~ s/\s+$//;

	if ($line eq "User Name:") {
	    print Writer "$user\n";
	}
	elsif ($line eq "Password:") {
	    print Writer "$passwd\n";
	}
	elsif ($line eq "Enter your handle [$user]:") {
	    print Writer "$player\n";
	    last;
	}
    }

    for (my $x = 0; $x < $BoardWidth; $x++) {
	for (my $y = 0; $y < $BoardHeight; $y++) {

	    while (my $line = <Reader>) {
		if ($line =~ /^\s+$/) {
		    next;
		}
		if (!($line =~ /\*/)) {
		    print "== $line";
		}

		$line =~ s/\s+$//;

		if ($line eq "player x y, or pass") {
		    print Writer "$player $x $y\n";
		    #sleep(5);
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
