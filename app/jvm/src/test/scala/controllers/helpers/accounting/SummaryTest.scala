// TODO: Fix this when API is stable

//package controllers.helpers.accounting
//
//import java.time.Month._
//import com.google.inject._
//import common.time.LocalDateTimes.createDateTime
//import common.testing._
//import scala.collection.immutable.Seq
//import scala.collection.JavaConverters._
//import org.junit.runner._
//import org.specs2.mutable._
//import org.specs2.runner._
//import play.api.test._
//import play.twirl.api.Html
//import common.time.{Clock, DatedMonth, MonthRange, TimeUtils}
//import common.TimeUtils.{April, February, January, March, May, dateAt}
//import common.testing.TestObjects._
//import common.testing.TestUtils._
//import common.testing.HookedSpecification
//import models._
//import models.accounting._
//import models.accounting.config.{Account, Category, Config}
//import models.accounting.money.{Money, ReferenceMoney}
//
//@RunWith(classOf[JUnitRunner])
//class SummaryTest extends HookedSpecification {
//
//  @Inject val summaries: Summaries = null
//  @Inject implicit val entityAccess: EntityAccess = null
//
//  override def before = {
//    Guice.createInjector(new FactoTestModule).injectMembers(this)
//
//    Clock.setTimeForTest(createDateTime(2010, APRIL, 4))
//  }
//
//  override def afterAll = Clock.cleanupAfterTest()
//
//  "Summary.fetchSummary()" should {
//    "caculate monthRangeForAverages" in new WithApplication {
//      persistTransaction(flowInCents = 200, date = createDateTime(2009, FEBRUARY, 2))
//      persistTransaction(flowInCents = 201, date = createDateTime(2009, FEBRUARY, 20))
//      persistTransaction(flowInCents = 202, date = createDateTime(2009, MARCH, 1))
//      persistTransaction(flowInCents = 202, date = createDateTime(2010, MAY, 4))
//
//      val summary = summaries.fetchSummary(testAccount, 2009)
//
//      summary.monthRangeForAverages shouldEqual MonthRange(createDateTime(2009, FEBRUARY, 1), createDateTime(2010, APRIL, 1))
//    }
//
//    "return successfully when there are no transactions" in new WithApplication {
//      val summary = summaries.fetchSummary(testAccount, 2009)
//      summary.totalRowTitles must not(beEmpty)
//    }
//
//    "ignore the current and future months when calculating the averages" in new WithApplication {
//      persistTransaction(flowInCents = 999, date = createDateTime(2009, APRIL, 2))
//      persistTransaction(flowInCents = 100, date = createDateTime(2010, FEBRUARY, 2))
//      persistTransaction(flowInCents = 112, date = createDateTime(2010, MARCH, 2))
//      persistTransaction(flowInCents = 120, date = createDateTime(2010, APRIL, 2))
//      persistTransaction(flowInCents = 130, date = createDateTime(2010, MAY, 2))
//      persistTransaction(flowInCents = 1999, date = createDateTime(2011, APRIL, 2))
//
//      val summary = summaries.fetchSummary(testAccount, 2009)
//
//      summary.yearToSummary(2010).categoryToAverages(testCategory) mustEqual ReferenceMoney(71)
//      summary.monthRangeForAverages shouldEqual MonthRange(createDateTime(2009, APRIL, 1), createDateTime(2010, APRIL, 1))
//    }
//
//
//    "ignore the pre-facto months when calculating the averages" in new WithApplication {
//      persistTransaction(flowInCents = 100, date = createDateTime(2010, FEBRUARY, 2))
//      persistTransaction(flowInCents = 112, date = createDateTime(2010, MARCH, 2))
//      persistTransaction(flowInCents = 120, date = createDateTime(2010, APRIL, 2))
//
//      val summary = summaries.fetchSummary(testAccount, 2009)
//
//      summary.yearToSummary(2010).categoryToAverages(testCategory) mustEqual ReferenceMoney(106)
//      summary.monthRangeForAverages shouldEqual MonthRange(createDateTime(2010, FEBRUARY, 1), createDateTime(2010, APRIL, 1))
//    }
//
//    "calculates totals" in new WithApplication {
//      persistTransaction(flowInCents = 3, date = createDateTime(2010, JANUARY, 2), category = testCategoryA)
//      persistTransaction(flowInCents = 100, date = createDateTime(2010, FEBRUARY, 2), category = testCategoryA)
//      persistTransaction(flowInCents = 102, date = createDateTime(2010, FEBRUARY, 2), category = testCategoryB)
//
//      val summary = summaries.fetchSummary(testAccount, 2009)
//
//      val totalRows = summary.yearToSummary(2010).totalRows
//      totalRows must haveSize(2)
//      totalRows(0).rowTitleHtml mustEqual Html("<b>Total</b>")
//      totalRows(0).monthToTotal(february(2010)) mustEqual ReferenceMoney(202)
//      totalRows(0).yearlyAverage mustEqual ReferenceMoney(68)
//      totalRows(1).rowTitleHtml mustEqual Html("<b>Total</b> (without catA)")
//      totalRows(1).monthToTotal(february(2010)) mustEqual ReferenceMoney(102)
//      totalRows(1).yearlyAverage mustEqual ReferenceMoney(34)
//
//      summary.totalRowTitles mustEqual Seq(Html("<b>Total</b>"), Html("<b>Total</b> (without catA)"))
//    }
//
//    "prunes unused categories" in new WithApplication {
//      persistTransaction(flowInCents = 3, date = createDateTime(2010, JANUARY, 2), category = testCategoryA)
//
//      val summary = summaries.fetchSummary(testAccount, 2010)
//
//      summary.categories must contain(testCategoryA)
//      summary.categories must not(contain(testCategoryB))
//    }
//  }
//
//  // ********** private helper methods ********** //
//  private def february(year: Int) = DatedMonth(createDateTime(year, FEBRUARY, 1))
//}
