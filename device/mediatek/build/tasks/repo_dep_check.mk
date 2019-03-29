REPO_DEP_CHECK := repo_dep_check
.PHONY: $(REPO_DEP_CHECK)
all_copied_headers: $(REPO_DEP_CHECK)

$(REPO_DEP_CHECK): repo_check_tool := device/mediatek/build/build/tools/repo_dep_check.py
$(REPO_DEP_CHECK): vars := PRODUCTS MTK_TARGET_PROJECT MTK_BASE_PROJECT LINUX_KERNEL_VERSION TARGET_ARCH PRODUCT_MODEL TARGET_BOARD_PLATFORM
$(REPO_DEP_CHECK): vars_value := $(foreach v,$(vars),$(v)=$(value $(v)))

$(REPO_DEP_CHECK):
	@echo "running repo_dep_check:"
	$(repo_check_tool) $(PRODUCT_MODEL) --vars "$(vars_value)"
	@echo "repo_dep_check pass"
