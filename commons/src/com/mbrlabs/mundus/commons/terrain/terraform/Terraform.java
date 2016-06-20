/*
 * Copyright (c) 2016. See AUTHORS file.
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

package com.mbrlabs.mundus.commons.terrain.terraform;

import com.mbrlabs.mundus.commons.terrain.Terrain;

/**
 * Provides static methods to manipulate the height of terrains.
 *
 * @author Marcus Brummer
 * @version 20-06-2016
 */
public class Terraform {

    public static PerlinNoiseGenerator perlin(Terrain terrain) {
        return new PerlinNoiseGenerator(terrain);
    }

    public static HeightMapGenerator heightMap(Terrain terrain) {
        return new HeightMapGenerator(terrain);
    }

}