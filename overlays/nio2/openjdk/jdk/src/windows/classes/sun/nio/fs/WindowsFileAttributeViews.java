/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 */

package sun.nio.fs;

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

import static sun.nio.fs.WindowsNativeDispatcher.*;
import static sun.nio.fs.WindowsConstants.*;

class WindowsFileAttributeViews {

    private static class Basic extends AbstractBasicFileAttributeView {
        final WindowsPath file;
        final boolean followLinks;

        Basic(WindowsPath file, boolean followLinks) {
            this.file = file;
            this.followLinks = followLinks;
        }


        public WindowsFileAttributes readAttributes() throws IOException {
            try {
                return WindowsFileAttributes.get(file, followLinks);
            } catch (WindowsException x) {
                x.rethrowAsIOException(file);
                return null;    // keep compiler happy
            }
        }

        /**
         * Parameter values in Windows times.
         */
        void setFileTimes(long createTime, long lastAccessTime, long lastWriteTime)
            throws IOException
        {
            long handle = -1L;
            try {
                int flags = FILE_FLAG_BACKUP_SEMANTICS;
                if (!followLinks && file.getFileSystem().supportsLinks())
                    flags |= FILE_FLAG_OPEN_REPARSE_POINT;

                handle = CreateFile(file.getPathForWin32Calls(),
                                    FILE_WRITE_ATTRIBUTES,
                                    (FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE),
                                    OPEN_EXISTING,
                                    flags);
            } catch (WindowsException x) {
                x.rethrowAsIOException(file);
            }

            // update attributes
            try {
                SetFileTime(handle, createTime, lastAccessTime, lastWriteTime);
            } catch (WindowsException x) {
                x.rethrowAsIOException(file);
            } finally {
                CloseHandle(handle);
            }
        }


        public void setTimes(Long lastModifiedTime,
                             Long lastAccessTime,
                             Long createTime,
                             TimeUnit unit) throws IOException
        {
            file.checkWrite();

            // if all null then do nothing
            if (lastModifiedTime == null && lastAccessTime == null &&
                createTime == null)
            {
                // no effect
                return;
            }

            // null => no change
            // -1 => change to current time
            long now = System.currentTimeMillis();
            long modTime = 0L, accTime = 0L, crTime = 0L;
            if (lastModifiedTime != null) {
                if (lastModifiedTime < 0L) {
                    if (lastModifiedTime != -1L)
                        throw new IllegalArgumentException();
                    modTime = now;
                } else {
                    modTime = TimeUnit.MILLISECONDS.convert(lastModifiedTime, unit);
                }
                modTime = WindowsFileAttributes.toWindowsTime(modTime);
            }
            if (lastAccessTime != null) {
                if (lastAccessTime < 0L) {
                    if (lastAccessTime != -1L)
                        throw new IllegalArgumentException();
                    accTime = now;
                } else {
                    accTime = TimeUnit.MILLISECONDS.convert(lastAccessTime, unit);
                }
                accTime = WindowsFileAttributes.toWindowsTime(accTime);
            }
            if (createTime != null) {
                if (createTime < 0L) {
                    if (createTime != -1L)
                        throw new IllegalArgumentException();
                    crTime = now;
                } else {
                    crTime = TimeUnit.MILLISECONDS.convert(createTime, unit);
                }
                crTime = WindowsFileAttributes.toWindowsTime(crTime);
            }

            setFileTimes(crTime, accTime, modTime);
        }
    }

    static class Dos extends Basic implements DosFileAttributeView {
        private static final String READONLY_NAME = "readonly";
        private static final String ARCHIVE_NAME = "archive";
        private static final String SYSTEM_NAME = "system";
        private static final String HIDDEN_NAME = "hidden";
        private static final String ATTRIBUTES_NAME = "attributes";

        Dos(WindowsPath file, boolean followLinks) {
            super(file, followLinks);
        }


        public String name() {
            return "dos";
        }


        public Object getAttribute(String attribute) throws IOException {
            if (attribute.equals(READONLY_NAME))
                return readAttributes().isReadOnly();
            if (attribute.equals(ARCHIVE_NAME))
                return readAttributes().isArchive();
            if (attribute.equals(SYSTEM_NAME))
                return readAttributes().isSystem();
            if (attribute.equals(HIDDEN_NAME))
                return readAttributes().isHidden();
            // implementation specific
            if (attribute.equals(ATTRIBUTES_NAME))
                return readAttributes().attributes();
            return super.getAttribute(attribute);
        }


        public void setAttribute(String attribute, Object value)
            throws IOException
        {
            if (attribute.equals(READONLY_NAME)) {
                setReadOnly((Boolean)value);
                return;
            }
            if (attribute.equals(ARCHIVE_NAME)) {
                setArchive((Boolean)value);
                return;
            }
            if (attribute.equals(SYSTEM_NAME)) {
                setSystem((Boolean)value);
                return;
            }
            if (attribute.equals(HIDDEN_NAME)) {
                setHidden((Boolean)value);
                return;
            }
            super.setAttribute(attribute, value);
        }


        public Map<String,?> readAttributes(String first, String[] rest)
            throws IOException
        {
            AttributesBuilder builder = AttributesBuilder.create(first, rest);
            WindowsFileAttributes attrs = readAttributes();
            addBasicAttributesToBuilder(attrs, builder);
            if (builder.match(READONLY_NAME))
                builder.add(READONLY_NAME, attrs.isReadOnly());
            if (builder.match(ARCHIVE_NAME))
                builder.add(ARCHIVE_NAME, attrs.isArchive());
            if (builder.match(SYSTEM_NAME))
                builder.add(SYSTEM_NAME, attrs.isSystem());
            if (builder.match(HIDDEN_NAME))
                builder.add(HIDDEN_NAME, attrs.isHidden());
            if (builder.match(ATTRIBUTES_NAME))
                builder.add(ATTRIBUTES_NAME, attrs.attributes());
            return builder.unmodifiableMap();
        }

        /**
         * Update DOS attributes
         */
        private void updateAttributes(int flag, boolean enable)
            throws IOException
        {
            file.checkWrite();

            // GetFileAttribtues & SetFileAttributes do not follow links so when
            // following links we need the final target
            String path = followLinks ? WindowsLinkSupport.getFinalPath(file) :
                                        file.getPathForWin32Calls();

            try {
                int oldValue = GetFileAttributes(path);
                int newValue = oldValue;
                if (enable) {
                    newValue |= flag;
                } else {
                    newValue &= ~flag;
                }
                if (newValue != oldValue) {
                    SetFileAttributes(path, newValue);
                }
            } catch (WindowsException x) {
                // don't reveal target in exception
                x.rethrowAsIOException(file);
            }
        }


        public void setReadOnly(boolean value) throws IOException {
            updateAttributes(FILE_ATTRIBUTE_READONLY, value);
        }


        public void setHidden(boolean value) throws IOException {
            updateAttributes(FILE_ATTRIBUTE_HIDDEN, value);
        }


        public void setArchive(boolean value) throws IOException {
            updateAttributes(FILE_ATTRIBUTE_ARCHIVE, value);
        }


        public void setSystem(boolean value) throws IOException {
            updateAttributes(FILE_ATTRIBUTE_SYSTEM, value);
        }

        // package-private
        // Copy given attributes to the file.
        void setAttributes(WindowsFileAttributes attrs)
            throws IOException
        {
            // copy DOS attributes to target
            int flags = 0;
            if (attrs.isReadOnly()) flags |= FILE_ATTRIBUTE_READONLY;
            if (attrs.isHidden()) flags |= FILE_ATTRIBUTE_HIDDEN;
            if (attrs.isArchive()) flags |= FILE_ATTRIBUTE_ARCHIVE;
            if (attrs.isSystem()) flags |= FILE_ATTRIBUTE_SYSTEM;
            updateAttributes(flags, true);

            // copy file times to target - must be done after updating FAT attributes
            // as otherwise the last modified time may be wrong.
            setFileTimes(
                WindowsFileAttributes.toWindowsTime(attrs.creationTime()),
                WindowsFileAttributes.toWindowsTime(attrs.lastModifiedTime()),
                WindowsFileAttributes.toWindowsTime(attrs.lastAccessTime()));
        }
    }

    static BasicFileAttributeView createBasicView(WindowsPath file, boolean followLinks) {
        return new Basic(file, followLinks);
    }

    static WindowsFileAttributeViews.Dos createDosView(WindowsPath file, boolean followLinks) {
        return new Dos(file, followLinks);
    }
}