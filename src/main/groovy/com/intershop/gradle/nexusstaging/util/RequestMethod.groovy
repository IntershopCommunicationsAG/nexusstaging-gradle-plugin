/*
 * Copyright 2015 Intershop Communications AG.
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
 *  limitations under the License.
 */


package com.intershop.gradle.nexusstaging.util

import groovy.transform.CompileStatic

@CompileStatic
enum  RequestMethod {
    GET {
        @Override
        public String toString() {
            return 'get'
        }
    },
    PUT {
        @Override
        public String toString() {
            return 'put'
        }
    },
    POST {
        @Override
        public String toString() {
            return 'post'
        }
    },
    DELETE {
        @Override
        public String toString() {
            return 'delete'
        }
    },
    PATCH {
        @Override
        public String toString() {
            return 'patch'
        }
    },
    HEAD {
        @Override
        public String toString() {
            return 'head'
        }
    }
}
