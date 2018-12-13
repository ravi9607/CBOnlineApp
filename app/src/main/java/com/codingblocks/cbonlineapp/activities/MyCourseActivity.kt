package com.codingblocks.cbonlineapp.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.codingblocks.cbonlineapp.R
import com.codingblocks.cbonlineapp.Utils.retrofitCallback
import com.codingblocks.cbonlineapp.adapters.TabLayoutAdapter
import com.codingblocks.cbonlineapp.database.*
import com.codingblocks.cbonlineapp.fragments.AnnouncementsFragment
import com.codingblocks.cbonlineapp.fragments.CourseContentFragment
import com.codingblocks.cbonlineapp.fragments.OverviewFragment
import com.codingblocks.cbonlineapp.utils.MediaUtils
import com.codingblocks.onlineapi.Clients
import com.google.android.youtube.player.YouTubeInitializationResult
import com.google.android.youtube.player.YouTubePlayer
import com.google.android.youtube.player.YouTubePlayerSupportFragment
import kotlinx.android.synthetic.main.activity_my_course.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import kotlin.concurrent.thread


class MyCourseActivity : AppCompatActivity(), AnkoLogger {

    lateinit var attemptId: String
    private lateinit var database: AppDatabase

    companion object {
        val YOUTUBE_API_KEY = "AIzaSyAqdhonCxTsQ5oQ-tyNaSgDJWjEM7UaEt4"
    }

    private lateinit var youtubePlayerInit: YouTubePlayer.OnInitializedListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_course)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = intent.getStringExtra("courseName")
        attemptId = intent.getStringExtra("attempt_id")
        database = AppDatabase.getInstance(this)

        val runDao = database.courseRunDao()
        val sectionDao = database.setionDao()
        val contentDao = database.contentDao()
        val courseDao = database.courseDao()
        val instructorDao = database.instructorDao()
        setupViewPager()

        courseDao.getCourse(attemptId).observe(this, Observer<Course> {
            youtubePlayerInit = object : YouTubePlayer.OnInitializedListener {
                override fun onInitializationFailure(p0: YouTubePlayer.Provider?, p1: YouTubeInitializationResult?) {
                }

                override fun onInitializationSuccess(p0: YouTubePlayer.Provider?, youtubePlayerInstance: YouTubePlayer?, p2: Boolean) {
                    if (!p2) {
                        if (it != null)
                            youtubePlayerInstance?.cueVideo(MediaUtils.getYotubeVideoId(it.promoVideo))
                    }
                }
            }
            val youTubePlayerSupportFragment = supportFragmentManager.findFragmentById(R.id.displayYoutubeVideo) as YouTubePlayerSupportFragment?
            youTubePlayerSupportFragment!!.initialize(YOUTUBE_API_KEY, youtubePlayerInit)
        })


        Clients.onlineV2JsonApi.enrolledCourseById(attemptId).enqueue(retrofitCallback { throwable, response ->
            response?.body()?.let { it ->

                val course = it.run?.course?.run {
                    Course(
                            id!!,
                            title!!,
                            subtitle!!,
                            logo!!,
                            summary!!,
                            promoVideo!!,
                            difficulty!!,
                            reviewCount!!,
                            rating!!,
                            slug!!,
                            coverImage!!,
                            attemptId,
                            updatedAt!!
                    )
                }

                val run = it.run?.run {
                    CourseRun(
                            id!!,
                            attemptId,
                            name!!,
                            description!!,
                            start!!,
                            end!!,
                            price!!,
                            mrp!!,
                            courseId!!,
                            updatedAt!!
                    )
                }

                thread {
                    courseDao.insert(course!!)
                    runDao.insert(run!!)

                    //Course Instructors List
                    for (instructor in it.run?.course!!.instructors!!) {
                        instructorDao.insert(Instructor(instructor.id!!, instructor.name!!,
                                instructor.description!!, instructor.photo!!,
                                instructor.updatedAt!!, attemptId, instructor.instructorCourse?.courseId!!))
                    }

                    //Course Sections List
                    for (section in it.run?.sections!!) {
                        sectionDao.insert(CourseSection(section.id!!, section.name!!,
                                section.order!!, section.premium!!, section.status!!,
                                section.run_id!!, attemptId, section.updatedAt!!))

                        //Section Contents List
                        val contents: ArrayList<CourseContent> = ArrayList()
                        for (content in section.contents!!) {
                            var contentDocument = ContentDocument()
                            var contentLecture = ContentLecture()
                            var contentVideo = ContentVideo()

                            when {
                                content.contentable.equals("lecture") -> content.lecture?.let { contentLecture = ContentLecture(it.id!!, it.name!!, it.duration!!, it.video_url!!, content.section_content?.id!!, it.updatedAt!!) }
                                content.contentable.equals("document") -> content.document?.let { contentDocument = ContentDocument(it.id!!, it.name!!, it.pdf_link!!, content.section_content?.id!!, it.updatedAt!!) }
                                content.contentable.equals("video") -> content.video?.let { contentVideo = ContentVideo(it.id!!, it.name!!, it.duration!!, it.description, it.url!!, content.section_content?.id!!, it.updatedAt!!) }
                            }
                            var progressId = ""
                            var status: String
                            if (content.progress != null) {
                                status = content.progress?.status!!
                                progressId = content.progress?.id!!
                            } else {
                                status = "UNDONE"
                            }
                            contents.add(CourseContent(
                                    content.id!!, status, progressId,
                                    content.title!!, content.duration!!,
                                    content.contentable!!, content.section_content?.order!!,
                                    content.section_content?.sectionId!!, attemptId, content.section_content?.updatedAt!!, contentLecture, contentDocument, contentVideo))
                        }

                        contentDao.insertAll(contents)
                    }
                }

            }
            info { "error ${throwable?.localizedMessage}" }

        })

    }


    private fun setupViewPager() {
        val adapter = TabLayoutAdapter(supportFragmentManager)
        adapter.add(OverviewFragment(), "Overview")
        adapter.add(AnnouncementsFragment(), "About")
        adapter.add(CourseContentFragment.newInstance(attemptId), "Course Content")
//        adapter.add(DoubtsFragment(), "")
        htab_viewpager.adapter = adapter
        htab_tabs.setupWithViewPager(htab_viewpager)
        htab_tabs.getTabAt(0)?.setIcon(R.drawable.ic_menu)
        htab_tabs.getTabAt(1)?.setIcon(R.drawable.ic_announcement)
        htab_tabs.getTabAt(2)?.setIcon(R.drawable.ic_docs)
        htab_tabs.getTabAt(2)?.select()
        htab_viewpager.offscreenPageLimit = 3
//        htab_tabs.getTabAt(3)?.setIcon(R.drawable.ic_support)

    }


}
