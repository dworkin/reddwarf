#!/usr/bin/ruby

COPYRIGHT_FILE = "COPYRIGHT"

S_SEEK_COMMENT_START = 1
S_SEEK_COMMENT_END   = 2
S_DONE               = 3

def process(input, output)
    changed = false
    state = S_SEEK_COMMENT_START
    input.each_line do |line|
	if state == S_SEEK_COMMENT_START
	    if line =~ /^\s*\/\*.*\*\/\s*$/
		STDERR.puts "#{$ARG}: WARNING one-line comment found"
		output.puts line
		state = S_DONE
	    elsif line =~ /^\s*\/\*\s*$/
		changed = true
		state = S_SEEK_COMMENT_END
	    elsif line =~ /^\s*\/\*\*\s*$/
		STDERR.puts "#{$ARG}: WARNING javadoc comment found"
		output.puts line
		state = S_DONE
	    elsif line =~ /^package/
		changed = true
		STDERR.puts "#{$ARG}: INFO no previous header comment"
		output.puts $COPYRIGHT
		output.puts ""
		output.puts line
		state = S_DONE
	    elsif line =~ /^\s*$/
		# Consume blank lines
		changed = true
	    else
		STDERR.puts("#{$ARG}: WARNING unexpected line:", line)
		output.puts line
		state = S_DONE
	    end
	elsif state == S_SEEK_COMMENT_END
	    if line =~ /\s*\*\/\s*$/
		changed = true
		output.puts $COPYRIGHT
		state = S_DONE
	    elsif line =~ /^((package)|(import))\s+\S+;/
		STDERR.puts("#{$ARG}: WARNING unexpected line:", line)
		output.puts line
	    else
		changed = true
		# Consume comment lines
	    end
	else
	    output.puts(line)
	end
    end
    return changed
end

def process_file(filename)
    $ARG = filename
    changed = false
    backup_filename = filename + '~'
    File.rename(filename, backup_filename)
    File.open(filename, "w") do |outfile|
	File.open(backup_filename, "r") do |infile|
	    changed = process(infile, outfile)
	end
    end
    File.unlink(backup_filename) unless changed
end


# main

File.open(COPYRIGHT_FILE) do |f|
    $COPYRIGHT = f.read
end

ARGV.each do |filename|
    next if filename =~ /\/\.svn\//
    unless File.readable?(filename)
	STDERR.puts "Can't read #{filename}, skipping"
	next
    end

    process_file(filename)
end
