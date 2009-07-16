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

// Base classes used to track various values through the compilation.
// SharkCompileInvariants is used to track values which remain the
// same for the top-level method and any inlined methods it may have
// (ie for the whole compilation).  SharkTargetInvariants is used to
// track values which differ between methods.

class SharkCompileInvariants : public ResourceObj {
 protected:
  SharkCompileInvariants(SharkCompiler* compiler, ciEnv* env)
    : _compiler(compiler),
      _env(env),
      _thread(NULL) {}

  SharkCompileInvariants(const SharkCompileInvariants* parent)
    : _compiler(parent->_compiler),
      _env(parent->_env),
      _thread(parent->_thread) {}

 private:
  SharkCompiler* _compiler;
  ciEnv*         _env;
  llvm::Value*   _thread;

  // The SharkCompiler that is compiling this method.  Holds the
  // classes that form the interface with LLVM (the builder, the
  // module, the memory manager, etc) and provides the compile()
  // method to convert LLVM functions to native code.
 protected:
  SharkCompiler* compiler() const
  {
    return _compiler;
  }

  // Top-level broker for HotSpot's Compiler Interface.
  //
  // Its main purpose is to allow the various CI classes to access
  // oops in the VM without having to worry about safepointing.  In
  // addition to this it acts as a holder for various recorders and
  // memory allocators.
  //
  // Accessing this directly is kind of ugly, so it's private.  Add
  // new accessors below if you need something from it.
 private:
  ciEnv* env() const
  {
    return _env;
  }

  // Pointer to this thread's JavaThread object.  This is not
  // available until a short way into SharkFunction creation
  // so a setter is required.  Assertions are used to enforce
  // invariance.
 protected:
  llvm::Value* thread() const
  {
    assert(_thread != NULL, "thread not available");
    return _thread;
  }
  void set_thread(llvm::Value* thread)
  {
    assert(_thread == NULL, "thread already set");
    _thread = thread;
  }
  
  // Objects that handle various aspects of the compilation.
 protected:
  SharkBuilder* builder() const
  {
    return compiler()->builder();
  }
  DebugInformationRecorder* debug_info() const
  {
    return env()->debug_info();
  }
  Dependencies* dependencies() const
  {
    return env()->dependencies();
  }

  // That well-known class...
 protected:
  ciInstanceKlass* java_lang_Object_klass() const
  {
    return env()->Object_klass();
  }
};

class SharkTargetInvariants : public SharkCompileInvariants {
 protected:
  SharkTargetInvariants(SharkCompiler* compiler, ciEnv* env, ciTypeFlow* flow)
    : SharkCompileInvariants(compiler, env),
      _target(flow->method()),
      _flow(flow),
      _max_monitors(count_monitors()) {}

  SharkTargetInvariants(const SharkCompileInvariants* parent, ciMethod* target)
    : SharkCompileInvariants(parent),
      _target(target),
      _flow(NULL),
      _max_monitors(count_monitors()) {}

  SharkTargetInvariants(const SharkTargetInvariants* parent)
    : SharkCompileInvariants(parent),
      _target(parent->_target),
      _flow(parent->_flow),
      _max_monitors(parent->_max_monitors) {}

 private:
  int count_monitors();

 private:
  ciMethod*   _target;
  ciTypeFlow* _flow;
  int         _max_monitors;

  // The method being compiled.
 protected:
  ciMethod* target() const
  {
    return _target;
  }

  // Typeflow analysis of the method being compiled.
 protected:
  ciTypeFlow* flow() const
  {
    assert(_flow != NULL, "typeflow not available");
    return _flow;
  }

  // Properties of the method.
 protected:
  int max_locals() const
  {
    return target()->max_locals();
  }
  int max_stack() const
  {
    return target()->max_stack();
  }
  int max_monitors() const
  {
    return _max_monitors;
  }
  int arg_size() const
  {
    return target()->arg_size();
  }
  bool is_static() const
  {
    return target()->is_static();
  }
  bool is_synchronized() const
  {
    return target()->is_synchronized();
  }
};