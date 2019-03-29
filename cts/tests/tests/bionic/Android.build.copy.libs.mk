LOCAL_PATH := $(call my-dir)

cts_bionic_tests_dir := lib32

ifeq (true,$(TARGET_IS_64_BIT))
  ifeq (,$(cts_bionic_tests_2nd_arch_prefix))
    cts_bionic_tests_dir := lib64
  endif
endif

# TODO(dimitry): Can this list be constructed dynamically?
my_bionic_testlib_files := \
  cfi_test_helper/cfi_test_helper \
  cfi_test_helper2/cfi_test_helper2 \
  dt_runpath_a/libtest_dt_runpath_a.so \
  dt_runpath_b_c_x/libtest_dt_runpath_b.so \
  dt_runpath_b_c_x/libtest_dt_runpath_c.so \
  dt_runpath_b_c_x/libtest_dt_runpath_x.so \
  libatest_simple_zip/libatest_simple_zip.so \
  libcfi-test.so \
  libcfi-test-bad.so \
  libdlext_test_different_soname.so \
  libdlext_test_fd/libdlext_test_fd.so \
  libdlext_test_norelro.so \
  libdlext_test_runpath_zip/libdlext_test_runpath_zip_zipaligned.zip \
  libdlext_test.so \
  libdlext_test_zip/libdlext_test_zip.so \
  libdlext_test_zip/libdlext_test_zip_zipaligned.zip \
  libdl_preempt_test_1.so \
  libdl_preempt_test_2.so \
  libdl_test_df_1_global.so \
  libsysv-hash-table-library.so \
  libtest_atexit.so \
  libtest_check_order_dlsym_1_left.so \
  libtest_check_order_dlsym_2_right.so \
  libtest_check_order_dlsym_3_c.so \
  libtest_check_order_dlsym_a.so \
  libtest_check_order_dlsym_b.so \
  libtest_check_order_dlsym_d.so \
  libtest_check_order_dlsym.so \
  libtest_check_order_reloc_root_1.so \
  libtest_check_order_reloc_root_2.so \
  libtest_check_order_reloc_root.so \
  libtest_check_order_reloc_siblings_1.so \
  libtest_check_order_reloc_siblings_2.so \
  libtest_check_order_reloc_siblings_3.so \
  libtest_check_order_reloc_siblings_a.so \
  libtest_check_order_reloc_siblings_b.so \
  libtest_check_order_reloc_siblings_c_1.so \
  libtest_check_order_reloc_siblings_c_2.so \
  libtest_check_order_reloc_siblings_c.so \
  libtest_check_order_reloc_siblings_d.so \
  libtest_check_order_reloc_siblings_e.so \
  libtest_check_order_reloc_siblings_f.so \
  libtest_check_order_reloc_siblings.so \
  libtest_check_rtld_next_from_library.so \
  libtest_dlopen_from_ctor_main.so \
  libtest_dlopen_from_ctor.so \
  libtest_dlopen_weak_undefined_func.so \
  libtest_dlsym_df_1_global.so \
  libtest_dlsym_from_this_child.so \
  libtest_dlsym_from_this_grandchild.so \
  libtest_dlsym_from_this.so \
  libtest_dlsym_weak_func.so \
  libtest_dt_runpath_d.so \
  libtest_empty.so \
  libtest_init_fini_order_child.so \
  libtest_init_fini_order_grand_child.so \
  libtest_init_fini_order_root2.so \
  libtest_init_fini_order_root.so \
  libtest_nodelete_1.so \
  libtest_nodelete_2.so \
  libtest_nodelete_dt_flags_1.so \
  libtest_pthread_atfork.so \
  libtest_relo_check_dt_needed_order_1.so \
  libtest_relo_check_dt_needed_order_2.so \
  libtest_relo_check_dt_needed_order.so \
  libtest_simple.so \
  libtest_two_parents_child.so \
  libtest_two_parents_parent1.so \
  libtest_two_parents_parent2.so \
  libtest_versioned_lib.so \
  libtest_versioned_libv1.so \
  libtest_versioned_libv2.so \
  libtest_versioned_otherlib_empty.so \
  libtest_versioned_otherlib.so \
  libtest_versioned_uselibv1.so \
  libtest_versioned_uselibv2_other.so \
  libtest_versioned_uselibv2.so \
  libtest_versioned_uselibv3_other.so \
  libtest_with_dependency_loop_a.so \
  libtest_with_dependency_loop_b.so \
  libtest_with_dependency_loop_b_tmp.so \
  libtest_with_dependency_loop_c.so \
  libtest_with_dependency_loop.so \
  libtest_with_dependency.so \
  prebuilt-elf-files/libtest_invalid-empty_shdr_table.so \
  prebuilt-elf-files/libtest_invalid-rw_load_segment.so \
  prebuilt-elf-files/libtest_invalid-unaligned_shdr_offset.so \
  prebuilt-elf-files/libtest_invalid-zero_shdr_table_content.so \
  prebuilt-elf-files/libtest_invalid-zero_shdr_table_offset.so \
  prebuilt-elf-files/libtest_invalid-zero_shentsize.so \
  prebuilt-elf-files/libtest_invalid-zero_shstrndx.so \
  prebuilt-elf-files/libtest_invalid-textrels.so \
  prebuilt-elf-files/libtest_invalid-textrels2.so \
  preinit_getauxval_test_helper/preinit_getauxval_test_helper \
  preinit_syscall_test_helper/preinit_syscall_test_helper \
  private_namespace_libs_external/libnstest_private_external.so \
  private_namespace_libs/libnstest_dlopened.so \
  private_namespace_libs/libnstest_private.so \
  private_namespace_libs/libnstest_root_not_isolated.so \
  private_namespace_libs/libnstest_root.so \
  public_namespace_libs/libnstest_public.so \
  public_namespace_libs/libnstest_public_internal.so \
  ld_preload_test_helper/ld_preload_test_helper \
  ld_preload_test_helper_lib1.so \
  ld_preload_test_helper_lib2.so \
  ld_config_test_helper/ld_config_test_helper \
  ns2/ld_config_test_helper_lib1.so \
  ns2/ld_config_test_helper_lib2.so \
  ld_config_test_helper_lib3.so \

# These libraries are not built for mips.
my_bionic_testlib_files_non_mips := \
  libgnu-hash-table-library.so \
  libtest_ifunc.so \
  libtest_ifunc_variable.so \
  libtest_ifunc_variable_impl.so \

my_bionic_testlibs_src_dir := \
  $($(cts_bionic_tests_2nd_arch_prefix)TARGET_OUT_DATA_NATIVE_TESTS)/bionic-loader-test-libs
my_bionic_testlibs_out_dir := $(cts_bionic_tests_dir)/bionic-loader-test-libs

LOCAL_COMPATIBILITY_SUPPORT_FILES += \
  $(foreach lib, $(my_bionic_testlib_files), \
    $(my_bionic_testlibs_src_dir)/$(lib):$(my_bionic_testlibs_out_dir)/$(lib))

ifneq ($(TARGET_ARCH),$(filter $(TARGET_ARCH),mips mips64))
LOCAL_COMPATIBILITY_SUPPORT_FILES += \
  $(foreach lib, $(my_bionic_testlib_files_non_mips), \
    $(my_bionic_testlibs_src_dir)/$(lib):$(my_bionic_testlibs_out_dir)/$(lib))
endif

my_bionic_testlib_files :=
my_bionic_testlib_files_non_mips :=
my_bionic_testlibs_src_dir :=
my_bionic_testlibs_out_dir :=
cts_bionic_tests_dir :=
cts_bionic_tests_2nd_arch_prefix :=
