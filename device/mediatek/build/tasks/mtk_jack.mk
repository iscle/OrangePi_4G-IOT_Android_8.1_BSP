# Use MTK jack env override
ifeq ($(USE_MTK_JACK_OVERRIDE),yes)

ifneq ($(dist_goal),)
# FIXME: override warning
setup-jack-server:
	@echo setup-jack-server: mtk-setup-jack-server
endif

setup-jack-server: mtk-setup-jack-server
setup-jack-server: PRIVATE_JACK_ADMIN := eval `cat $(MTK_JACK_ENV)` prebuilts/sdk/tools/jack-admin
.PHONY: mtk-setup-jack-server
mtk-setup-jack-server:
ifndef jack_server_disabled
	$(hide) mkdir -p $(dir $(MTK_JACK_ENV))
	python device/mediatek/build/build/tools/jack_find_port.py find_port $(MTK_JACK_ENV) 127.0.0.1 50000 60000 2 $(notdir $(PRIVATE_SERVER_JAR))
ifneq ($(dist_goal),)
	$(hide) $(PRIVATE_JACK_ADMIN) stop-server 2>&1 || (exit 0)
	$(hide) $(PRIVATE_JACK_ADMIN) kill-server 2>&1 || (exit 0)
	$(hide) $(PRIVATE_JACK_ADMIN) uninstall-server 2>&1 || (exit 0)
endif
	$(hide) $(PRIVATE_JACK_ADMIN) install-server $(PRIVATE_PATH)/jack-launcher.jar $(PRIVATE_SERVER_JAR)  2>&1 || (exit 0)
	python device/mediatek/build/build/tools/jack_find_port.py update_client $(MTK_JACK_ENV) 127.0.0.1 50000 60000 2 $(notdir $(PRIVATE_SERVER_JAR))
ifneq ($(dist_goal),)
	$(hide) mkdir -p "$(DIST_DIR)/logs/jack/"
	$(hide) JACK_SERVER_VM_ARGUMENTS="$(jack_vm_args) -Dcom.android.jack.server.log.file=$(abspath $(DIST_DIR))/logs/jack/jack-server-%u-%g.log" $(PRIVATE_JACK_ADMIN) start-server 2>&1 || exit 0
	$(hide) $(PRIVATE_JACK_ADMIN) update server $(PRIVATE_SERVER_JAR) $(PRIVATE_SERVER_VERSION) 2>&1 || exit 0
	$(hide) $(foreach jack_jar,$(available_jack_jars),$(PRIVATE_JACK_ADMIN) update jack $(jack_jar) $(patsubst $(PRIVATE_PATH)/jacks/jack-%.jar,%,$(jack_jar)) || exit 47;)
endif
	rm -f `cat $(MTK_JACK_ENV) | grep JACK_CLIENT_SETTING | sed -e 's/JACK_CLIENT_SETTING=//' -e 's/\/mtk-jack-\S\+\/\.jack-settings/\/mtk-jack-override\/.jack-override/'`.lock
endif

endif
