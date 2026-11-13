                    containerColor = SurfaceDark,
                    contentColor = PinkSoft
                ) {
                    listOf(
                        "home" to "Home",
                        "get" to "Get Code",
                        "import" to "I Have Code",
                        "timeline" to "Timeline",
                        "library" to "Library",
                        "projects" to "Projects"
                    ).forEach { (key, label) ->

                        NavigationBarItem(
                            selected = page == key,
                            onClick = { page = key },

                            icon = {
                                val navIcon = when (key) {
                                    "home" -> Icons.Default.Home
                                    "get" -> Icons.Default.AutoAwesome
                                    "import" -> Icons.Default.Code
                                    "library" -> Icons.Default.LibraryMusic
                                    "projects" -> Icons.Default.Folder
                                    else -> Icons.Default.GraphicEq
                                }

                                Icon(
                                    imageVector = navIcon,
                                    contentDescription = label
                                )
                            },

                            label = {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    fontWeight = if (page == key) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Medium
                                    }
                                )
                            },

                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF260D1B),
                                selectedTextColor = PinkSoft,
                                indicatorColor = PinkPrimary,
                                unselectedIconColor = Color(0xFFB99AAA),
                                unselectedTextColor = Color(0xFFB99AAA)
                            )
                        )
                    }
                }
            },

            content = { paddingValues ->

                when (page) {

                    "home" -> {
                        LazyColumn(
                            modifier = Modifier
                                .padding(paddingValues)
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {

                            item {
                                Text(
                                    "Your sound design workspace",
                                    fontSize = 24.sp,
                                    color = TextMain,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    "Script → AI JSON → Assets → Timeline → MP4",
                                    color = TextMuted
                                )
                            }

                            item {
                                HomeTile(
                                    "GET CODE",
                                    "Create a strict AI video-analysis prompt"
                                ) {
                                    page = "get"
                                }
                            }

                            item {
                                HomeTile(
                                    "I HAVE CODE",
                                    "Import JSON and your original video"
                                ) {
                                    page = "import"
                                }
                            }

                            item {
                                HomeTile(
                                    "TIMELINE & EXPORT",
                                    "Review audio events and render final MP4"
                                ) {
                                    page = "timeline"
                                }
                            }

                            item {
                                HomeTile(
                                    "AUDIO LIBRARY",
                                    "Manage your offline SFX and music collection"
                                ) {
                                    page = "library"
                                }
                            }

                            item {
                                HomeTile(
                                    "MY PROJECTS",
                                    "Open, edit, update or delete saved projects"
                                ) {
                                    savedProjects = ProjectLibrary.load(ctx)
                                    page = "projects"
                                }
                            }

                            if (project != null) {
                                item {
                                    Button(
                                        onClick = { saveCurrentProject() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = null
                                        )

                                        Text(
                                            if (activeSavedId == null) {
                                                "Save Project"
                                            } else {
                                                "Update Saved Project"
                                            }
                                        )
                                    }
                                }
                            }

                            item {
                                Text(
                                    "${project?.name ?: "No project loaded"}\n" +
                                        "$videoName\n" +
                                        "${assets.count { it.uri != null }} / ${assets.size} assets assigned\n" +
                                        status,
                                    color = TextMuted
                                )
                            }
