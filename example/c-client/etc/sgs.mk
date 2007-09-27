# Include file for SGS Makefile

BINDIR  = $(TOPDIR)/bin
OBJDIR  = .obj

.DEFAULT: all

OS = $(shell uname)
include $(TOPDIR)/etc/mk/$(OS).mk

OBJS = $(addprefix $(OBJDIR)/, $(SRCS:.c=.o))

$(OBJDIR)/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c -o $@ $<

clean.default:
	@/bin/rm -rf $(OBJDIR)

realclean.default: clean

.PHONY: all clean realclean
