Default: all

include ../DeveloperTools/install/common.mk

CLASSLIB=$(JAVA_HOME)/jre/lib/rt.jar
RSRC=rsrc
LIBDIR=$(RSRC)/lib
LIB=$(subst $(SPACE),$(CLN),$(filter %.jar %.zip, $(wildcard $(LIBDIR)/*)))
BUILD=build
SRC=src
DOCS=docs
CLASSPATH="$(CLASSLIB)$(CLN)$(LIB)$(CLN)$(SRC)"

include classes.mk

CLASS_FILES:=$(foreach class, $(CLASSES), $(BUILD)/$(subst .,/,$(class)).class)
PACKAGES=$(sort $(basename $(CLASSES)))
PACKAGEDIRS=$(subst .,/,$(PACKAGES))

JKEYRING=jKeyring.jar

all: $(JKEYRING)

$(JKEYRING): classes
	$(JAR) cvf $@ -C $(BUILD)/ .

javadocs:
	mkdir -p $(DOCS)
	$(JAVADOC) -d $(DOCS) -classpath $(CLASSPATH) $(PACKAGES)

clean:
	rm -f $(JKEYRING)
	rm -rf $(BUILD)

classes: classdirs $(CLASS_FILES)

classdirs: $(foreach pkg, $(PACKAGEDIRS), $(BUILD)/$(pkg)/)

$(BUILD)/%.class: $(SRC)/%.java
	$(JAVAC) $(JAVACFLAGS) -d $(BUILD) -classpath $(CLASSPATH) $<

$(BUILD)/%/:
	mkdir -p $(subst PKG,,$@)

test:
	$(JAVA) -classpath "$(LIB)$(CLN)$(JKEYRING)" jkeyring.Test2
