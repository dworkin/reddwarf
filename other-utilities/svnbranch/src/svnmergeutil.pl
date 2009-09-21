# svnmergeutil.pl -- Utilities to support branch merging in Subversion

# $Id: svnmergeutil.pl 4411 2008-06-12 18:13:45Z tb97327 $

################
# Use
################

use strict;
use warnings;
use Getopt::Std;

################
# Variables
################

# The possible values for the scheme portion of a repository URL,
# separated by vertical bars.
my $repos_schemes = "file|http|https|svn";

# The required major version of Subversion.
my $svn_major_version = "1";

# The required minimum minor version of Subversion.
my $svn_minor_version = "6";

# The value of the SVNDIR environment variable, with the final path
# separator removed, if present.
my $svndir = eval {
    my $dir = $ENV{"SVNDIR"};
    if ($dir && $dir =~ m!^(.*)/$!) {
	$dir = "$1";
    }
    return $dir;
};

# The location of the svn executable.
our $svn = $svndir ? "$svndir/svn" : "svn";

################
# Utility subroutines
################

# Returns the repository URL for a working copy directory.  Returns an
# empty string if the argument is not a working copy directory.
sub get_wc_url {
    my ($wc) = @_;
    # A cheap check that $wc is a working copy
    if (! -d "$wc/.svn") {
	return "";
    }
    for (command("$svn info " . escape($wc))) {
	if (/^URL: (.+)$/) {
	    return "$1";
	}
    }
    die "Error: Repository URL not found for directory: $wc";
}

# Returns the latest revision of the specified repository URL.  Returns
# the empty string if the revision is not found.
sub get_url_revision {
    my ($url) = @_;
    my ($head, $tail) = $url =~ m!^(.*)/([^/]+)/?$!;
    # Always get the latest revisions from the repository.  The 'svn
    # status -u' command does not always provide the latest revision for
    # the top level directory of a working copy if modifications were
    # made to subdirectories or files, so use 'svn list' instead.
    for (command("$svn list -v $head")) {
	if (/^ *(\d+).* $tail\/$/) {
	    return $1;
	}
    }
    return "";
}

# Returns the revision for when a branch was first copied from the
# trunk.  Signals an error if the revision is not found.  The first
# argument specifies the working copy or URL for the branch, the second
# argument is the latest revision to consider when searching for the
# copy point.
sub get_branch_first_revision {
    my ($branch, $latest_rev) = @_;
    my $command = "$svn log -qv --stop-on-copy -r 1:$latest_rev " .
	escape($branch);
    my $branch_url =
	is_repository_url($branch) ? $branch : get_wc_url($branch);
    my $base_url = get_base_url($branch_url);
    my ($path) = $branch_url =~ m!^$base_url(.*)$!;
    my $revision = 0;
    for (command($command)) {
	if ($revision && /^Changed paths/) {
	    # This is the start of information for files in the second
	    # branch revision, not the one which created the branch, so
	    # we're done
	    last;
	} elsif (/^   A $path.* \(from .*:(\d+)\)$/) {
	    # Use the trunk revision for this file if it is higher than
	    # the last seen
	    if ($1 > $revision) {
		$revision = $1;
	    }
	}
    }
    if ($revision) {
	return $revision;
    } else {
	die "Error: Branch revision not found: $branch\n";
    }
}

# Returns a string that describes the 'svn mv' commands used to move
# files in the results of a call to 'svn log -v'.  Returns an empty
# string if there were no moves in the log.  The first argument
# specifies the repository URL to which the moves are relative.  The
# second argument is a list containing the output of the 'svn log -v'
# command.
sub get_moves {
    my ($repository_url, @output) = @_;
    my $base_url = get_base_url($repository_url);
    my ($path) = $repository_url =~ m!^$base_url(.*)$!;
    # Set to 1 while parsing pathname info.  Otherwise set to 0, meaning
    # that the contents are user log comments and should be ignored.
    my $paths = 0;
    # Stores information about files that have been added and deleted,
    # to identify files that have been moved.  Maps a location to itself
    # to represent a deleted file.  Maps an old location to a new one to
    # represent a newly added file that was previously in another
    # location.  A moved file is one for which the file has been added
    # to a new location and deleted from the old one.
    my %mappings;
    my $result = "";
    for (@output) {
	if (/^Changed paths:$/) {
	    $paths = 1;
	    # Clear out the mappings -- only match adds and deletes
	    # within a single commit
	    %mappings = ();
	} elsif (/^$/) {
	    $paths = 0;
	} elsif (!$paths) {
	    next;
	} elsif (/^   D (.*)$/) {
	    my $from = $1;
	    if ($from =~ m!^.*$path/(.*)$!) {
		$from = $1;
		my $to = $mappings{$from};
		if ($to) {
		    $result .= "  svn mv $from $to\n";
		    delete $mappings{$from};
		} else {
		    $mappings{$from} = $from;
		}
	    }
	} elsif (/^   A (.*) \(from (.*):\d+\)$/) {
	    my $to = $1;
	    my $from = $2;
	    if ($to =~ m!^.*$path/(.*)$!) {
		$to = $1;
		if ($from =~ m!^.*$path/(.*)$!) {
		    $from = $1;
		    my $old = $mappings{$from};
		    if ($old && $old eq $from) {
			$result .= "  svn mv $from $to\n";
		    } else {
			$mappings{$from} = $to;
		    }
		}
	    }
	}
    }
    return $result;
}

# Execute the first argument as a command and returns standard output as
# a list.  Dies if the command fails.
sub command {
    my ($command) = @_;
    my @result = `$command 2>&1`;
    if ($? != 0) {
	my $message = @result ? join("\n", @result) : $!;
	die "Error: Command failed: \'$command\': $message\n";
    }
    return @result;
}

# Checks a working copy for updates and, optionally, modifications.  The
# first argument specifies the working copy, and the second argument
# specifies whether to check for modifications.  Returns a string that
# describes the problem if one is found, otherwise an empty string.
sub get_update_modified {
    my ($working_copy, $check_modified) = @_;
    my $update = "^........[*].*\$";
    my $modified = "^([^ ?]|.[CM]).*\$";
    my @status = command("$svn status -uv " . escape($working_copy));
    my $found_update = 0;
    my $found_modified = 0;
    for (@status) {
	if (/^Status against revision/) {
	    next;
	}
	if (/$update/) {
	    $found_update = 1;
	}
	if ($check_modified && /$modified/) {
	    $found_modified = 1;
	}
    }
    if ($found_update) {
	return $found_modified
	    ? "needs to be updated and is modified" : "needs to be updated";
    } else {
	return $found_modified ? "is modified" : "";
    }
}

# Checks if the argument is a Subversion repository URL.
sub is_repository_url {
    my ($arg) = @_;
    return $arg =~ /^($repos_schemes):/;
}

# Returns a pathname string with spaces and shell meta characters
# escaped with backslashes.
sub escape {
    my ($pathname) = @_;
    # Put a backslash in front of the space character, as well as all of
    # the characters with special meaning in the shell:
    # ! " \ $ & ' ( ) * ; < > ? [ \ ] ` { | }
    $pathname =~ s/([ !"\$&'()*;<>?[\\\]`{|}])/\\$1/g;
    return $pathname;
}

# Returns the revision to which the specified working directory or URL
# has been refreshed, or the empty string if the information is not
# recorded.  The first argument specifies the working directory or URL,
# the second argument is the name of the branch, and the third argument
# is the latest revision to consider when searching for the revision in
# which the branch was created.
sub get_refreshed {
    my ($arg, $branch_name, $latest_rev) = @_;
    my $file = "$arg/.svnbranch/refresh_$branch_name";
    my $result = read_line_if_exists($file);
    if (!$result) {
	# Branch not refreshed, use the initial branch revision instead
	$result = get_branch_first_revision($arg, $latest_rev);
    }
    return $result;
}

# Returns the first line of the specified pathname or URL if it exists,
# else the empty string.
sub read_line_if_exists {
    my ($file) = @_;
    my $result;
    if (is_repository_url($file)) {
	chomp($result = `$svn cat $file 2>&1`);
	if ($? != 0) {
	    if ($result =~ /^svn: (File|.*\n?.* path) not found/) {
		$result = "";
	    } else {
		my $message = $result || $!;
		die "Error: Command failed: \'$svn cat $file\': $message\n";
	    }
	}
    } elsif (-e $file) {
	if (!open(REFRESH, "<", $file)) {
	    die "Error: Problem opening file \'$file\': $!\n";
	}
	chomp($result = <REFRESH>);
	close(REFRESH);
    } else {
	$result = "";
    }
    return $result;
}

# Sets the revision the specified working directory has been refreshed
# to.  The first argument is the working directory, the second is the
# revision.
sub set_refreshed {
    my ($wc, $revision, $branch_name) = @_;
    my $directory = $wc . "/.svnbranch";
    if (!-e $directory) {
	command("$svn mkdir " . escape($directory));
    }
    my $file = "$directory/refresh_$branch_name";
    my $file_exists = -e $file;
    if (!open(REVISION, ">", $file)) {
	die "Error: Problem writing file \'$file\': $!\n";
    }
    print REVISION "$revision\n";
    close(REVISION);
    if (!$file_exists) {
	command("$svn add " . escape($file));
    }
}

# Returns the revision for which the specified working directory or URL
# has had the specified branch name merged up, or the empty string if
# the information is not recorded.
sub get_merged_up {
    my ($arg, $branch_name) = @_;
    my $file = "$arg/.svnbranch/mergeup_$branch_name";
    return read_line_if_exists($file);
}

# Sets the revision for which the specified working directory has had
# the specified branch name merged up.  The first argument is the
# working directory, the second is the revision, and the third is the
# branch name.
sub set_merged_up {
    my ($wc, $revision, $branch_name) = @_;
    my $directory = $wc . "/.svnbranch";
    if (!-e $directory) {
	command("$svn mkdir " . escape($directory));
    }
    my $file = "$directory/mergeup_$branch_name";
    my $file_exists = -e $file;
    if (!open(REVISION, ">", $file)) {
	die "Error: Problem writing file \'$file\': $!\n";
    }
    print REVISION "$revision\n";
    close(REVISION);
    if (!$file_exists) {
	command("$svn add " . escape($file));
    }
}

# Returns the portion of the URL that is associated with the repository
# itself, as opposed to subdirectories and files within the repository.
sub get_base_url {
    my ($url) = @_;
    # Use 'svn list' to go up the tree until we fail.  Set $next to
    # everything except the last slash, if present.
    my ($next) = $url =~ m!^(.*?)/?$!;
    my $root = "";
    # Stop if we moved up too high
    while ($next !~ m!^($repos_schemes):/+$!) {
	# FIXME: Distinguish server failure from non-repository
	# -tjb@sun.com (05/31/2005)
	if (0 == system("$svn list $next > /dev/null 2>&1")) {
	    $root = $next;
	    ($next) = $next =~ m!^(.*)/[^/]+$!;
	} else {
	    last;
	}
    }
    if (!$root) {
	die "Error: Repository not found: $url\n";
    } else {
	return $root;
    }
}

# Prints a warning message if the first argument is true, otherwise dies.
sub warn_or_die {
    my ($warn, $msg) = @_;
    if ($warn) {
	warn "Warning: $msg";
    } else {
	die "Error: $msg";
    }
}

# Check for the correct version of Subversion.  We require an exact
# match for the major version and at least the minimum minor version,
# with no requirement on the third version component.
sub check_svn_version {
    my ($current_version) = command("$svn --version --quiet");
    chomp($current_version);
    my ($major, $minor) = $current_version =~ m!^([0-9]+)[.]([0-9]+)([.].*)?!;
    if ($major != $svn_major_version || $minor < $svn_minor_version) {
	die "Error: Found version $current_version of Subversion, but" .
	    " require version $svn_major_version\.$svn_minor_version";
    }
}

1;
