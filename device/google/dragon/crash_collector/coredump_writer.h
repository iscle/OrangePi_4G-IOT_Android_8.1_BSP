/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef COREDUMP_WRITER_H_
#define COREDUMP_WRITER_H_

#include <map>
#include <string>
#include <vector>

#include <elf.h>
#include <link.h>

#include <android-base/macros.h>

class CoredumpWriter {
 public:
  // Coredump will be read from |fd_src|, and will be written to
  // |coredump_filename|. Additional files which are necessary to generate
  // minidump will be generated under |proc_files_dir|.
  CoredumpWriter(int fd_src,
                 const std::string& coredump_filename,
                 const std::string& proc_files_dir);
  ~CoredumpWriter();

  // Writes coredump and returns the number of bytes written, or -1 on errors.
  ssize_t WriteCoredump();

  size_t coredump_size_limit() const { return coredump_size_limit_; }
  size_t expected_coredump_size() const { return expected_coredump_size_; }

 private:
  using Ehdr = ElfW(Ehdr);
  using Phdr = ElfW(Phdr);

  // Virtual address occupied by a mapped file.
  using FileRange = std::pair<long, long>;
  struct FileInfo {
    long offset;
    std::string path;
  };
  using FileMappings = std::map<FileRange, FileInfo>;

  class FdReader;

  ssize_t WriteCoredumpToFD(int fd_dest);

  // Reads ELF header, all program headers, and NOTE segment from fd_src.
  bool ReadUntilNote(FdReader* reader,
                     Ehdr* elf_header,
                     std::vector<Phdr>* program_headers,
                     std::vector<char>* note_buf);

  // Extracts a set of address ranges occupied by mapped files from NOTE segment.
  bool GetFileMappings(const std::vector<char>& note_buf,
                       FileMappings* file_mappings);

  // Filters out unneeded segments.
  void FilterSegments(const std::vector<Phdr>& program_headers,
                      const FileMappings& file_mappings,
                      std::vector<Phdr>* program_headers_filtered);

  // Writes the contents of NT_AUXV note to a file.
  bool WriteAuxv(const std::vector<char>& note_buf,
                 const std::string& output_path);

  // Writes mapping info to a file in the same format as /proc/PID/maps.
  // (cf. "man proc")
  bool WriteMaps(const std::vector<Phdr>& program_headers,
                 const FileMappings& file_mappings,
                 const std::string& output_path);

  const int fd_src_;
  const std::string coredump_filename_;
  const std::string proc_files_dir_;
  size_t coredump_size_limit_ = 0;
  size_t expected_coredump_size_ = 0;

  DISALLOW_COPY_AND_ASSIGN(CoredumpWriter);
};

#endif  // COREDUMP_WRITER_H_
