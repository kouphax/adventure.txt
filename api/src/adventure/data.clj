(ns adventure.data)

(def initial-seed-data
  [{ :title "Your Adventure Ends Here"
     :description "A most boring adventure where nothing mush happens no matter how hard you try"
     :author "James Hughes"
     :sections [{ :content "Make a decision"
                  :options { "Do one thing" 1
                             "Do another"   2 }}
                { :content "You did one thing. Want to start again?"
                  :options { "Yes" 0
                             "No"  2 }}
                { :content "Your adventure ends here" }]}])
