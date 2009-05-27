/*
 * Copyright 1999-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * Copyright 2008, 2009 Red Hat, Inc.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

class SharkState;

class SharkStateScanner : public StackObj {
 protected:
  SharkStateScanner(SharkFunction* function)
    : _function(function) {}

 private:
  SharkFunction* _function;

 protected:
  SharkFunction* function() const
  {
    return _function;
  }

  // Scan the frame
 public:
  void scan(SharkState* state);

  // Callbacks
  // Note that the offsets supplied to the various process_* callbacks
  // are specified in wordSize words from the frame's unextended_sp.
 protected:
  virtual void start_frame()                                                 {}

  virtual void start_stack(int num_slots, int max_slots)                     {}
  virtual void process_stack_slot(int index, SharkValue** value, int offset) {}
  virtual void end_stack()                                                   {}

  virtual void start_monitors(int num_monitors)                              {}
  virtual void process_monitor(int index, int box_offset, int obj_offset)    {}
  virtual void end_monitors()                                                {}

  virtual void start_frame_header()                                          {}
  virtual void process_oop_tmp_slot(llvm::Value** value, int offset)         {}
  virtual void process_method_slot(llvm::Value** value, int offset)          {}
  virtual void process_pc_slot(int offset)                                   {}
  virtual void end_frame_header()                                            {}
  
  virtual void start_locals(int num_locals)                                  {}
  virtual void process_local_slot(int index, SharkValue** value, int offset) {}
  virtual void end_locals()                                                  {}

  virtual void end_frame()                                                   {}

  // Integrity checks
 private:
  static void stack_integrity_checks(SharkState* state) PRODUCT_RETURN;
  static void locals_integrity_checks(SharkState* state) PRODUCT_RETURN;
};
