;; Puts its function, which returns 42, at index 0 of the holder's table.
(module
  (import "holder" "tab" (table 2 funcref))
  (global $g i32 (i32.const 42))

  (func $answer (result i32) global.get $g)

  (elem (i32.const 0) $answer)
)
