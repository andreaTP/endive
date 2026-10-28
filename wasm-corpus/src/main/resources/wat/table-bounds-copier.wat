;; Copies the owner's entry into a table of its own.
(module
  (import "owner" "tab" (table 1 funcref))
  (table $own 1 funcref)

  (func (export "copy")
    (table.set $own (i32.const 0) (table.get 0 (i32.const 0))))
)
