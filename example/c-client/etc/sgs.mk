# Include file for SGS Makefile

OBJDIR  = .obj

.DEFAULT: all

OS = $(shell uname)
include $(TOPDIR)/etc/$(OS).mk

OBJS = $(addprefix $(OBJDIR)/, $(SRCS:.c=.o))

$(OBJDIR)/%.o: %.c
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -c -o $@ $<

.PHONY: all clean realclean
